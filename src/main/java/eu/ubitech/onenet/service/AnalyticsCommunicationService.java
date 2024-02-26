package eu.ubitech.onenet.service;

import eu.ubitech.onenet.config.PropertiesConfiguration;
import eu.ubitech.onenet.exceptions.AnalyticsCommunicationException;
import eu.ubitech.onenet.model.SecurityReportHitsCount;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.FilterAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.filter.ParsedFilter;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.domain.PageRequest;

@Slf4j
@Service
public class AnalyticsCommunicationService {

    private static final String index_base = "connectors-";
    private static final String index_wild = "connectors-*";
    private static final int max_elastic_page_size = 10000; // limitation set by elastic
    private static final String CONNECTOR_ID_FIELD = "headers.x_forwarded_for.keyword";

    private final ElasticsearchOperations elasticsearchOperations;
    private final RestHighLevelClient client;

    private final PropertiesConfiguration config;
    private final WebClient analyticsClient;
    private final String analyticsUrl;

    public AnalyticsCommunicationService(
            PropertiesConfiguration config,
            Builder builder,
            ElasticsearchOperations elasticsearchOperations,
            RestHighLevelClient client) {
        this.config = config;
        this.analyticsUrl = config.getAnalyticsUrl();
        this.analyticsClient = builder.build();
        this.elasticsearchOperations = elasticsearchOperations;
        this.client = client;
    }

    public Object getAnomalyResults(String connector, String minutes){
        try {
            String uri = analyticsUrl.concat("/api/v1/analytics/anomaly_detection/get_predictions/");

            log.info("Getting anomaly results for the last {} minutes", minutes);

            // Get anomaly detection results from analytics service
            ResponseSpec retrieve = analyticsClient.get()
                    .uri(uri)
                    .header("minutes", minutes)
                    .retrieve();

            HttpStatus status = retrieve.toBodilessEntity().block().getStatusCode();
            Object result = retrieve.bodyToMono(Object.class).block();

            if (status != HttpStatus.OK) {
                log.error("Analytics returned status: {}", status);
                throw new AnalyticsCommunicationException();
            }

            log.debug("anomaly_detection result {}", result.toString());

            ArrayList<String> allIps = new ArrayList<String>();

            // Get all ips
            for (Object timeslot: (ArrayList)result) {
                List<String> ips = (List<String>) ((LinkedHashMap) timeslot).get("ip");
                allIps.addAll(ips);
            }
            log.debug("anomaly_detection result all ips {}", allIps);

            if (connector != null) {
                // The goal is to find all clients whose IP appears in the logs
                // of the specified connector.

                BoolQueryBuilder query = QueryBuilders.boolQuery();

                // Connector query
                QueryBuilder connectorQuery = QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query.must(connectorQuery);

                // Client IP query
                QueryBuilder clientIPsQuery = QueryBuilders.termsQuery("client_geoip.ip.keyword", allIps);
                query.must(clientIPsQuery);

                Query searchQuery = new NativeSearchQueryBuilder()
                    .withPageable(PageRequest.of(0, max_elastic_page_size))
                    .withQuery(query)
                    .withSourceFilter(new FetchSourceFilter(
                                new String[]{
                                    "client_geoip.ip"
                                },
                                new String[]{}))
                    .build();

                // Aggregate by client IP
                String agg_name = "get_ip";
                AggregationBuilder aggregation = AggregationBuilders
                    .terms(agg_name)
                    .field("client_geoip.ip.keyword");

                SearchRequest searchRequest = new SearchRequest(index_wild);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.query(query).aggregation(aggregation);
                searchRequest.source(searchSourceBuilder);

                SearchResponse searchResponse =
                    client.search(searchRequest, RequestOptions.DEFAULT);

                ParsedStringTerms results = searchResponse.getAggregations().get(agg_name);
                List<? extends Bucket> bucketList = results.getBuckets();

                ArrayList<String> foundIps = new ArrayList<String>();
                bucketList.forEach(b -> {
                    log.debug("ip: [{}], total hits: [{}]",
                              b.getKeyAsString(),
                              b.getDocCount());

                    foundIps.add(b.getKeyAsString());
                });

                log.debug("allIps {}", allIps);
                log.debug("foundIps {}", foundIps);

                // Edit result and remove IPs not contained in foundIps
                for (Object timeslot: (ArrayList)result) {
                    List<String> ips = (List<String>) ((LinkedHashMap) timeslot).get("ip");
                    List<Double> ip_statuses = (List<Double>) ((LinkedHashMap) timeslot).get("ip_status");
                    for (int i = 0; i < ips.size(); i++) {
                        if (!foundIps.contains(ips.get(i))) {
                            ips.remove(i);
                            ip_statuses.remove(i);
                            // Decrement counter since we removed an element
                            // and ips.size() changed
                            i--;
                        }
                    }
                }
                log.debug("edited anomaly_detection result {}", result.toString());
            }

            return result;
        } catch (Exception e) {
            log.error("Could not get analytics, Exception: {}", e.getMessage());
            throw new AnalyticsCommunicationException();
        }
    }

    // Gets anomaly detection result and queries Elasticsearch to return more
    // information about the abnormal IPs
    public Object getSecurityReport(){
        try {
            String uri = analyticsUrl.concat("/api/v1/analytics/anomaly_detection/get_predictions/");

            ResponseSpec retrieve = analyticsClient.get()
                    .uri(uri)
                    .header("minutes", "60")
                    .retrieve();

            HttpStatus status = retrieve.toBodilessEntity().block().getStatusCode();
            Object result = retrieve.bodyToMono(Object.class).block();

            if (status != HttpStatus.OK) {
                log.error("Analytics returned status: {}", status);
                throw new AnalyticsCommunicationException();
            }

            log.debug("analytics result is");
            log.debug(result.toString());

            ArrayList<String> abnormalIps = new ArrayList<String>();

            // We only need abnormal IPs
            for (Object timeslot: (ArrayList)result) {
                List<String> ips = (List<String>) ((LinkedHashMap) timeslot).get("ip");
                List<Double> ip_statuses = (List<Double>) ((LinkedHashMap) timeslot).get("ip_status");
                for (int i = 0; i < ips.size(); i++) {
                    // log.debug(ips.get(i).toString());
                    // log.debug(ip_statuses.get(i).toString());

                    // Negative status means IP behavior was abnormal
                    if (ip_statuses.get(i) < 0) {
                        abnormalIps.add(ips.get(i).toString());
                    }
                }
            }
            log.info("Abnormal IPs are: {}", abnormalIps.toString());

            log.info("Starting security report aggregations");

            List<SecurityReportHitsCount> securityReportHitsCountList = new ArrayList<>();

            // List<String> abnormalIpsTest = new ArrayList<>();
            // abnormalIpsTest.add("46.177.251.62");
            // abnormalIpsTest.add("213.249.38.66");
            // log.info("Abnormal IPs (Test) are: {}", abnormalIpsTest.toString());

            // Get more information regarding these abnormal IPs
            try {
                Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
                Instant roundedSince = now.minus(60, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
                log.debug("instants: {} {}", now, roundedSince);

                // Time range query
                RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                        .lte(now.toString())
                        .gte(roundedSince.toString());

                // Abnormal IP query
                // Only look for IPs that were classified as abnormal
                TermsQueryBuilder abnormalIpsQuery = QueryBuilders
                    .termsQuery("client_geoip.ip.keyword", abnormalIps);

                // Add time range and abnormal IP queries
                BoolQueryBuilder query = QueryBuilders.boolQuery()
                    .must(abnormalIpsQuery)
                    .must(rangeQuery);

                // Hits per IP
                TermsAggregationBuilder aggregationHitsPerIp = AggregationBuilders
                    .terms("hits_per_ip")
                    .field("client_geoip.ip.keyword");

                // Country aggregation within hits per IP
                TermsAggregationBuilder aggregationCountryOfIp = AggregationBuilders
                    .terms("country_code_of_ip")
                    .field("client_geoip.country_code2.keyword");

                // Errors per IP (response codes greater than 399 - 40x, 50x)
                FilterAggregationBuilder aggregationErrorsPerIp = AggregationBuilders
                    .filter("errors_per_ip",
                            QueryBuilders.rangeQuery("response").gt(399));

                // Add nested aggregations
                aggregationHitsPerIp.subAggregation(aggregationCountryOfIp);
                aggregationHitsPerIp.subAggregation(aggregationErrorsPerIp);

                SearchRequest searchRequest = new SearchRequest(index_wild);
                SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
                searchSourceBuilder.size(0);
                searchSourceBuilder.query(query).aggregation(aggregationHitsPerIp);

                searchRequest.source(searchSourceBuilder);

                SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

                ParsedStringTerms aggregationResult = searchResponse.getAggregations().get("hits_per_ip");

                for (Bucket bucket : aggregationResult.getBuckets()) {
                    String ip = bucket.getKeyAsString();
                    long hitsCount = bucket.getDocCount();

                    // Get the nested country aggregation
                    ParsedStringTerms bucketCountry = bucket.getAggregations().get("country_code_of_ip");
                    // There should be only one bucket so no need to iterate
                    // over buckets, just get bucket 0
                    String countryCode = bucketCountry.getBuckets().get(0).getKeyAsString();

                    // Get the nested errors aggregation
                    ParsedFilter bucketErrors = bucket.getAggregations().get("errors_per_ip");
                    long errorsCount = bucketErrors.getDocCount();

                    // System.out.println("IP: " + ip);
                    // System.out.println("Country: " + countryCode);
                    // System.out.println("Hit Count: " + hitsCount);
                    // System.out.println("Errors: " + errorsCount);
                    // System.out.println();

                    securityReportHitsCountList
                        .add(new SecurityReportHitsCount(
                                    ip, countryCode, hitsCount, errorsCount));

                }

                return securityReportHitsCountList;

            } catch (Exception e) {
                log.error(e.getMessage());
            }

            return securityReportHitsCountList;

            // return abnormalIps;
        } catch (Exception e) {
            log.error("Could not get security report, Exception: {}", e.getMessage());
            throw new AnalyticsCommunicationException();
        }
    }
}
