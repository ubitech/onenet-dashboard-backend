/**
 * This is the main class for the business logic of live monitoring of connectors logs transactions. NOTE that the form is not final
 * and this is still WIP.
 *
 * The assumption we make is that every log is a transaction. This means we can just count logs and not
 * query the actual content and manipulate it in post.
 * This make all the network traffic less by orders of magnitude, and increased the speed
 *
 * Two functions query24hourEntries and queryDayEntries are left intentionally currently non used, to demonstrate how to get the actual data
 *
 * The two main functions are queryLastMonth and query24hourEntriesCount.
 * The operations are done in two different ways to demonstrate each functionality.
 * - queryLastMonth creates 30 index strings, one for each day and performs 30 queries. This is not optimal
 * but it may be needed eventually if we will perform actual operations on data
 * Since it is not live data we dont mind
 *
 * - query24hourEntriesCount uses the date histogram feature of elastic, where we create a final form for our data in hourly buckets
 * with a query on a specific range on our index, using a wildcard "connectors-*".
 * This is the most optimal it can be because we use only the optimized elastic operations and perform zero post-processing
 * It is used for live data Server Sent Events, each cycle is some msec only.
 * The trigger of SSE is explained in WIKI
 *
 * queryLastMonth can also be ported to date histogram using daily buckets
 *
 * TROUBLESHOOTING
 * if problems arise, it is probably by changes in the format of timestamps or index format.
 * In general any changes in Elastic stack and log generation/manipulation in the code backend
 */


package eu.ubitech.onenet.service;

import eu.ubitech.onenet.dto.HttpTransactionsDto;
import eu.ubitech.onenet.dto.AdvancedFilteringDto;
import eu.ubitech.onenet.model.ConnectorLogs;
import eu.ubitech.onenet.model.CountryHitsCount;
import eu.ubitech.onenet.model.StackedSeriesDataPoint;
import eu.ubitech.onenet.model.AdvancedFilteringResult;
import eu.ubitech.onenet.model.HealthCheckResult;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import org.elasticsearch.search.aggregations.bucket.histogram.Histogram;
import org.elasticsearch.search.aggregations.bucket.histogram.LongBounds;
import org.elasticsearch.search.aggregations.bucket.histogram.ParsedDateHistogram;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.metrics.ParsedMax;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NetworkMonitoringService {

    private static final String index_base = "connectors-";
    private static final String index_wild = "connectors-*";
    private static final int max_elastic_page_size = 10000; // limitation set by elastic
    // The field we use to distinguish between connectors
    private static final String CONNECTOR_ID_FIELD = "headers.x_forwarded_for.keyword";


    private final ElasticsearchOperations elasticsearchOperations;
    private final RestHighLevelClient client;

    public NetworkMonitoringService(
            ElasticsearchOperations elasticsearchOperations,
            RestHighLevelClient client) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.client = client;
    }

    /**
     * Function that returns a DTO containing transactions per day for the last 30 days
     *
     * @param connector - connector id
     * @return DTO of the HTTP transactions
     */
    public HttpTransactionsDto queryLastMonth(String connector) {

        log.debug("Starting queryLastMonth");
        HttpTransactionsDto dto = new HttpTransactionsDto();

        // Create a List of days for using the indexes
        LocalDate weekBeforeToday = LocalDate.now().minusDays(15);
        List<String> daysList = IntStream.rangeClosed(1, 15)
                .mapToObj(weekBeforeToday::plusDays)
                .map(x -> x.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")))// this is the format convention we have used for indexes in ELK, as separated by logstash
                .collect(Collectors.toList());

        // perform 30 queries for the last 30 days, take the day as string created above
        // and concatenate the index string eg connectors-2022.08.22 and get count for that day/index
        List<Long> indexCountList = daysList.stream()
                .map(day -> queryDayEntriesCount(index_base + day, connector)).collect(Collectors.toList());

        dto.setXaxis(daysList);
        dto.setYaxis(indexCountList);

        log.debug("daysList: {}", daysList);
        log.debug("indexCountList: {}", indexCountList);

        return dto;
    }

    /**
     * Function that returns a long representing the count of logs for a day
     *
     * @param elasticIndex - index of the day to search
     * @param connector - connector id
     * @return DTO of the HTTP transactions
     */
    private long queryDayEntriesCount(String elasticIndex, String connector) {
        log.debug("Starting queryDayEntriesCount for index: {}", elasticIndex);
        long totalCount = 0L;
        try {
            Query query;
            if (connector == null) {
                query = new NativeSearchQueryBuilder()
                    .withQuery(new MatchAllQueryBuilder())
                    .build();
            }
            else {
                TermQueryBuilder connectorQuery =
                    QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query = new NativeSearchQueryBuilder()
                    .withQuery(connectorQuery)
                    .build();
            }

            totalCount = elasticsearchOperations
                    .count(query, IndexCoordinates.of(elasticIndex));

            log.info("For index: {} , total count: {}", elasticIndex, totalCount);
        } catch (NoSuchIndexException e){
            log.info("Empty index: {}", elasticIndex);
        }
        catch (Exception e) {
            log.error(e.getMessage());
        }
        return totalCount;
    }

    /**
     * Function that returns list of parsed ConnectorLogs containing all info like message, agent id
     * etc of the past 24 hours
     *
     * NOT USED currently. Its better that it stays to show how we can get the actual logs data and
     * maybe perform operations on them
     *
     * This function is limited to 10000 entries per index as it is the maximum page size of
     * elastic. If we expect heavier load, a paging operation has to be implemented
     *
     * @return List<ConnectorLogs>
     */
    private List<ConnectorLogs> query24hourEntries() {
        log.debug("Starting query24hourEntries");
        try {
            QueryBuilders.rangeQuery("@timestamp").lte("now").gte("now-24h");

            Query searchQuery = new NativeSearchQueryBuilder()
                    .withPageable(PageRequest.of(0, max_elastic_page_size))
                    .withQuery(QueryBuilders.rangeQuery("@timestamp").lte("now").gte("now-24h"))
                    .withSourceFilter(new FetchSourceFilter(
                            new String[]{"@timestamp", "message", "agent.id", "client_geoip.country_name", "client_geoip.country_code2"},
                            new String[]{}))
                    .build();

            long totalCount = elasticsearchOperations
                    .count(searchQuery, IndexCoordinates.of(index_wild));

            log.debug("query24hourEntries count result: {}", totalCount);

            SearchHits<ConnectorLogs> hits =
                    elasticsearchOperations
                            .search(searchQuery,
                                    ConnectorLogs.class,
                                    IndexCoordinates.of(index_wild));

            List<ConnectorLogs> logList = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            // use this for heavy debugging, it may flood logs with hundreds thousands lines...
            log.trace("query24hourEntries List: {}", logList);

            return logList;

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * Function that DTO of HTTP transactions, used for live data for SSE
     *
     * It implements the date histogram feature of elastic resulting in the quickest way possible to
     * get the last 24h data in hourly buckets
     *
     * @param connector - connector id
     * @return HttpTransactionsDto
     */
    public HttpTransactionsDto query24hourEntriesCount(String connector) {
        log.debug("Starting query24hourEntriesCount");

        HttpTransactionsDto dto = new HttpTransactionsDto();
        String agg_name = "query_per_hour";

        try {
            // get the timestamp now and timestamp 24 hours ago, rounded up in hours
            // eg 2022-08-04T14:42:09Z 2022-08-03T14:00:00Z
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant rounded24hAgo = now.minus(24, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
            log.debug("instants: {} {}", now, rounded24hAgo);

            // create a query aggregation for the date histogram
            // do not make changes in any of the parameters without fully understanding the consequences
            DateHistogramAggregationBuilder aggregation = AggregationBuilders
                    .dateHistogram(agg_name)
                    .field("@timestamp")
                    .fixedInterval(DateHistogramInterval.HOUR)
                    .minDocCount(0) // this along with the below, means that the empty buckets will be returned even if they are empty
                    .missing(0)
                    .extendedBounds(
                            new LongBounds(rounded24hAgo.toString(), now.toString())
                    );

            // both ranges should be set, here and the aggregation as extended bounds
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                    .lte(now.toString())
                    .gte(rounded24hAgo.toString());


            // if connector argument is given, only search for specific connector logs
            QueryBuilder query;
            if (connector == null) {
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery);
            }
            else {
                TermQueryBuilder connectorQuery =
                    QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery)
                    .must(connectorQuery);
            }

            // we search with wildcard, since the last 24 contain two indices, eg half day now half day yesterday
            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query).aggregation(aggregation);
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            ParsedDateHistogram dateGroupBy = searchResponse.getAggregations()
                    .get(agg_name);

            log.debug("query24hourEntriesCount total count: {}",
                    searchResponse.getHits().getTotalHits().value);

            // here we got the final data, we need to fill our dto
            List<? extends Histogram.Bucket> bucketList = dateGroupBy.getBuckets();

            List<String> dates = new ArrayList<>();
            List<Long> hitsCount = new ArrayList<>();

            bucketList.forEach(b -> {
                dates.add(getFormattedString(b.getKeyAsString()));
                hitsCount.add(b.getDocCount());
            });

            log.debug("dates list result: {}", dates);
            log.debug("hitsCount list result: {}", hitsCount);

            dto.setYaxis(hitsCount);
            dto.setXaxis(dates);

            return dto;
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return dto;
    }


    /**
     * Function that returns list of parsed ConnectorLogs containing all info like message, agent id
     * etc of each day
     *
     * NOT USED currently. Its better that it stays to show how we can get the actual logs data and
     * maybe perform operations on them
     *
     * This function is limited to 10000 entries per index as it is the maximum page size of
     * elastic. If we expect heavier load, a paging operation has to be implemented
     *
     * @return List<ConnectorLogs>
     */
    private List<ConnectorLogs> queryDayEntries(String elasticIndex) {
        log.info("Starting queryDayEntries with index: {}", elasticIndex);
        try {
            // With the use of Source filter you can query the selected fields we need only, and not the whole entry
            // thus reducing by far the size of data fetched, since its also gotten with REST
            Query searchQuery = new NativeSearchQueryBuilder()
                    .withPageable(PageRequest.of(0, max_elastic_page_size))
                    .withQuery(new MatchAllQueryBuilder())
                    .withSourceFilter(new FetchSourceFilter(
                            new String[]{"@timestamp", "message", "agent.id"},
                            new String[]{}))
                    .build();

            SearchHits<ConnectorLogs> hits =
                    elasticsearchOperations
                            .search(searchQuery,
                                    ConnectorLogs.class,
                                    IndexCoordinates.of(elasticIndex));

            List<ConnectorLogs> logList = hits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            log.debug("Final Log list: {}", logList);

            return logList;

        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return null;
    }

    /**
     * Function that returns a count of hits per country for the last 30 days
     *
     * @param connector - connector id
     * @return List<CountryHitsCount>
     */
    public List<CountryHitsCount> aggregateHitsPerCountry(String connector) {
        log.info("Starting aggregateHitsPerCountry");

        List<CountryHitsCount> countryHitsCountList = new ArrayList<>();
        String agg_name = "hits_per_country";
        String sub_agg_name = "get_country_name";

        try {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant rounded30dAgo = now.minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            log.debug("instants: {} {}", now, rounded30dAgo);

            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                    .lte(now.toString())
                    .gte(rounded30dAgo.toString());

            // Use this sub aggregation to also get the country_name field
            AggregationBuilder subAggregation = AggregationBuilders
                .terms(sub_agg_name)
                .field("client_geoip.country_name.keyword");

            AggregationBuilder aggregation = AggregationBuilders
                .terms(agg_name)
                .field("client_geoip.country_code2.keyword")
                .subAggregation(subAggregation);

            QueryBuilder query;
            if (connector == null) {
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery);
            }
            else {
                // Only search for logs from the specified connector
                TermQueryBuilder connectorQuery =
                    QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery)
                    .must(connectorQuery);
            }

            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query).aggregation(aggregation);
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            log.debug("total count: {}",
                      searchResponse.getHits().getTotalHits().value);

            ParsedStringTerms hitsPerCountry = searchResponse.getAggregations().get(agg_name);

            List<? extends Bucket> bucketList = hitsPerCountry.getBuckets();

            // For each country returned, populate countryHitsCountList
            bucketList.forEach(b -> {
                ParsedStringTerms countryName = b.getAggregations().get(sub_agg_name);

                List<? extends Bucket> subBucketList = countryName.getBuckets();

                // Use the sub aggregation to get the country name
                subBucketList.forEach(sb -> {
                    log.debug("country code: [{}], country name: [{}], hits: [{}]",
                             b.getKeyAsString(),
                             sb.getKeyAsString(),
                             b.getDocCount());

                    countryHitsCountList
                        .add(new CountryHitsCount(
                                b.getKeyAsString(),
                                sb.getKeyAsString(),
                                new Long(b.getDocCount())));
                });
            });

            return countryHitsCountList;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return countryHitsCountList;
    }


    /**
     * Function that returns the sum of bytes sent of all requests the last 3 days
     *
     * @param connector - connector id
     * @return HttpTransactionsDto
     */
    public HttpTransactionsDto aggregateRecentBytesSent(String connector) {
        log.info("Starting aggregateRecentBytesSent");

        HttpTransactionsDto dto = new HttpTransactionsDto();

        String agg_name = "per_day";
        String sub_agg_name = "sum_bytes_sent";

        try {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant rounded3dAgo = now.minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            log.debug("instants: {} {}", now, rounded3dAgo);

            // Only get data for the last 3 days
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                    .lte(now.toString())
                    .gte(rounded3dAgo.toString());

            // Get the sum of bytes sent
            AggregationBuilder subAggregation = AggregationBuilders
                .sum(sub_agg_name)
                .field("bytes");

            // Aggregate data per day
            DateHistogramAggregationBuilder aggregation = AggregationBuilders
                    .dateHistogram(agg_name)
                    .field("@timestamp")
                    .fixedInterval(DateHistogramInterval.DAY)
                    .minDocCount(0)
                    .missing(0)
                    .extendedBounds(
                            new LongBounds(rounded3dAgo.toString(), now.toString())
                    )
                    .subAggregation(subAggregation);

            QueryBuilder query;
            if (connector == null) {
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery);
            }
            else {
                // Only search for logs from the specified connector
                TermQueryBuilder connectorQuery =
                    QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery)
                    .must(connectorQuery);
            }

            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query).aggregation(aggregation);
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse =
                client.search(searchRequest, RequestOptions.DEFAULT);

            log.debug("total count: {}",
                      searchResponse.getHits().getTotalHits().value);

            ParsedDateHistogram dateGroupBy = searchResponse.getAggregations()
                .get(agg_name);

            List<? extends Histogram.Bucket> bucketList = dateGroupBy.getBuckets();

            List<String> dates = new ArrayList<>();
            List<Long> bytesSent = new ArrayList<>();

            // For each day, populate dates and bytesSent
            bucketList.forEach(b -> {
                ParsedSum bytesSentThisDay = b
                    .getAggregations()
                    .get(sub_agg_name);

                log.debug("date: [{}], total bytes: [{}]",
                          getFormattedString(b.getKeyAsString()),
                          bytesSentThisDay.getValue());

                dates.add(b.getKeyAsString());
                bytesSent.add((long) bytesSentThisDay.getValue());
            });

            dto.setYaxis(bytesSent);
            dto.setXaxis(dates);

            return dto;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return dto;
    }

    /**
     * Function that returns a list of response codes of all requests the last 3 days
     *
     * @param connector - connector id
     * @return HttpTransactionsDto
     */
    public List<StackedSeriesDataPoint> aggregateRecentResponseCodes(String connector) {
        log.info("Starting aggregateRecentResponseCodes");

        List<StackedSeriesDataPoint> stackedSeriesData = new ArrayList<>();

        String agg_name = "per_day";
        String sub_agg_name = "hits_per_response_code";

        try {
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            Instant rounded3dAgo = now.minus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
            log.debug("instants: {} {}", now, rounded3dAgo);

            // Only get data for the last 3 days
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("@timestamp")
                    .lte(now.toString())
                    .gte(rounded3dAgo.toString());

            // Aggregate per response code
            AggregationBuilder subAggregation = AggregationBuilders
                .terms(sub_agg_name)
                .field("response.keyword");

            // Aggregate data per day
            DateHistogramAggregationBuilder aggregation = AggregationBuilders
                    .dateHistogram(agg_name)
                    .field("@timestamp")
                    .fixedInterval(DateHistogramInterval.DAY)
                    .minDocCount(0)
                    .missing(0)
                    .extendedBounds(
                            new LongBounds(rounded3dAgo.toString(), now.toString())
                    )
                    .subAggregation(subAggregation);

            QueryBuilder query;
            if (connector == null) {
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery);
            }
            else {
                // Only search for logs from the specified connector
                TermQueryBuilder connectorQuery =
                    QueryBuilders.termQuery(CONNECTOR_ID_FIELD, connector);
                query = QueryBuilders.boolQuery()
                    .must(rangeQuery)
                    .must(connectorQuery);
            }

            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(query).aggregation(aggregation);

            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse =
                client.search(searchRequest, RequestOptions.DEFAULT);

            log.debug("total count: {}",
                      searchResponse.getHits().getTotalHits().value);

            ParsedDateHistogram dateGroupBy = searchResponse.getAggregations()
                .get(agg_name);

            List<? extends Histogram.Bucket> bucketList = dateGroupBy.getBuckets();

            // For each day
            bucketList.forEach(b -> {
                log.debug("date: [{}], total hits: [{}] -------------------",
                          getFormattedString(b.getKeyAsString()),
                          b.getDocCount());

                // Add dummy data point for each date so that no bucket is missing
                StackedSeriesDataPoint dummyDataPoint = new StackedSeriesDataPoint();
                dummyDataPoint.setCategory(b.getKeyAsString()); // Date
                dummyDataPoint.setName(null); // null Response Code so it does not show up in the chart
                dummyDataPoint.setDataPoint(new Long(0)); // Number of responses
                stackedSeriesData.add(dummyDataPoint);

                ParsedStringTerms responsesPerResponseCode = b
                    .getAggregations()
                    .get(sub_agg_name);

                List<? extends Bucket> subBucketList = responsesPerResponseCode
                    .getBuckets();

                // For each response code
                subBucketList.forEach(sb -> {
                    log.debug("date: [{}], response code: [{}], hits: [{}]",
                             b.getKeyAsString(),
                             sb.getKeyAsString(),
                             sb.getDocCount());
                    StackedSeriesDataPoint dataPoint = new StackedSeriesDataPoint();
                    dataPoint.setCategory(b.getKeyAsString()); // Date
                    dataPoint.setName(sb.getKeyAsString()); // Response code
                    dataPoint.setDataPoint(sb.getDocCount()); // Number of responses
                    stackedSeriesData.add(dataPoint);
                });

            });

            return stackedSeriesData;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return stackedSeriesData;
    }

    /**
     * Function that returns a list of all the connectors from which we have
     * received logs
     *
     * @return List<String>
     */
    public List<String> queryConnectors() {
        log.info("Starting queryConnectors");

        List<String> connectors = new ArrayList<>();

        String agg_name = "per_connector";

        try {
            AggregationBuilder aggregation = AggregationBuilders
                .terms(agg_name)
                .field(CONNECTOR_ID_FIELD);

            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.aggregation(aggregation);
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse =
                client.search(searchRequest, RequestOptions.DEFAULT);

            log.debug("total count: {}",
                      searchResponse.getHits().getTotalHits().value);


            ParsedStringTerms results = searchResponse.getAggregations().get(agg_name);

            List<? extends Bucket> bucketList = results.getBuckets();

            // For each result, populate connectors array
            bucketList.forEach(b -> {
                log.debug("connector: [{}], total hits: [{}]",
                          b.getKeyAsString(),
                          b.getDocCount());

                connectors.add(b.getKeyAsString());
            });

            return connectors;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return connectors;
    }

    /**
     * Function that returns a list of all the connectors from which we have
     * received logs along with the timestamp of the last log
     *
     * @return List<HealthCheckResult>
     */
    public List<HealthCheckResult> getHealthCheck() {
        log.info("Starting getHealthCheck");

        List<HealthCheckResult> healthCheckResults = new ArrayList<>();

        String sub_agg_name = "latest_timestamp";
        String agg_name = "per_connector";

        try {
            // Get the most recent timestamp
            AggregationBuilder subAggregation = AggregationBuilders
                .max(sub_agg_name)
                .field("@timestamp");

            // Aggregate per connector ID
            AggregationBuilder aggregation = AggregationBuilders
                .terms(agg_name)
                .field(CONNECTOR_ID_FIELD);

            SearchRequest searchRequest = new SearchRequest(index_wild);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.aggregation(aggregation.subAggregation(subAggregation));
            searchRequest.source(searchSourceBuilder);

            SearchResponse searchResponse =
                client.search(searchRequest, RequestOptions.DEFAULT);

            ParsedStringTerms results = searchResponse.getAggregations().get(agg_name);

            List<? extends Bucket> bucketList = results.getBuckets();

            // For each connector, populate healthCheckResult
            bucketList.forEach(b -> {
                ParsedMax latestTimestamp = b
                    .getAggregations()
                    .get(sub_agg_name);

                log.debug("connector: [{}], last log timestamp: [{}]",
                         b.getKeyAsString(),
                         (long) latestTimestamp.getValue());


                HealthCheckResult healthCheckResult = new HealthCheckResult();
                healthCheckResult.setConnector(b.getKeyAsString());
                healthCheckResult.setTimestamp((long) latestTimestamp.getValue());
                healthCheckResults.add(healthCheckResult);
            });

            return healthCheckResults;

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        return healthCheckResults;
    }

    /**
     * Function that returns a list of all the connectors from which we have
     * received logs
     *
     * @return List<String>
     */
    public List<AdvancedFilteringResult> doAdvancedFiltering(AdvancedFilteringDto filter) {
        log.info("Starting doAdvancedFiltering");
        log.debug(filter.toString());

        List<AdvancedFilteringResult> advancedFilteringResults = new ArrayList<>();

        try {
            BoolQueryBuilder query = QueryBuilders.boolQuery();

            // >>> connector filter
            if (filter.connector != null) {
                QueryBuilder connectorQuery = QueryBuilders.termQuery(CONNECTOR_ID_FIELD, filter.connector);
                query.must(connectorQuery);
            }

            // >>> dateFrom and dateTo filter
            log.debug("dateFrom and dateTo: {} {}", filter.dateFrom, filter.dateTo);
            if (!((filter.dateFrom == null) && (filter.dateTo == null))) {
                QueryBuilder dateRangeQuery = null;
                if (filter.dateFrom == null) {
                    Instant dateTo = Instant.parse(filter.dateTo);
                    // Search from "beginning of time" until dateTo
                    dateRangeQuery = QueryBuilders.rangeQuery("@timestamp")
                        .lte(dateTo.toString());
                }
                else if (filter.dateTo == null) {
                    Instant dateFrom = Instant.parse(filter.dateFrom);
                    // Search from dateFrom until now
                    dateRangeQuery = QueryBuilders.rangeQuery("@timestamp")
                        .gte(dateFrom.toString());
                }
                else {
                    Instant dateTo = Instant.parse(filter.dateTo);
                    Instant dateFrom = Instant.parse(filter.dateFrom);
                    dateRangeQuery = QueryBuilders.rangeQuery("@timestamp")
                        .lte(dateTo.toString())
                        .gte(dateFrom.toString());
                }
                query.must(dateRangeQuery);
            }

            // >>> bytesSentMin and bytesSentMax filter
            log.debug("bytesSentMin and bytesSentMax: {} {}", filter.bytesSentMin, filter.bytesSentMax);
            if (!((filter.bytesSentMin == null) && (filter.bytesSentMax == null))) {
                QueryBuilder bytesRangeQuery = null;
                if (filter.bytesSentMin == null) {
                    bytesRangeQuery = QueryBuilders.rangeQuery("bytes")
                        .lte(Integer.parseInt(filter.bytesSentMax));
                }
                else if (filter.bytesSentMax == null) {
                    bytesRangeQuery = QueryBuilders.rangeQuery("bytes")
                        .gte(Integer.parseInt(filter.bytesSentMin));
                }
                else {
                    bytesRangeQuery = QueryBuilders.rangeQuery("bytes")
                        .lte(Integer.parseInt(filter.bytesSentMax))
                        .gte(Integer.parseInt(filter.bytesSentMin));
                }
                query.must(bytesRangeQuery);
            }

            // >>> clientIPs filter
            if (filter.clientIPs != null && filter.clientIPs.size() != 0) {
                QueryBuilder clientIPsQuery = QueryBuilders.termsQuery("client_geoip.ip.keyword", filter.clientIPs);
                query.must(clientIPsQuery);
            }

            // >>> requestMethods filter
            if (filter.requestMethods != null && filter.requestMethods.size() != 0) {
                QueryBuilder requestMethodsQuery = QueryBuilders.termsQuery("verb.keyword", filter.requestMethods);
                query.must(requestMethodsQuery);
            }

            // >>> responseCodes filter
            if (filter.responseCodes != null && filter.responseCodes.size() != 0) {
                QueryBuilder responseCodesQuery = QueryBuilders.termsQuery("response.keyword", filter.responseCodes);
                query.must(responseCodesQuery);
            }

            // >>> countries filter
            if (filter.countries != null && filter.countries.size() != 0) {
                QueryBuilder countriesQuery = QueryBuilders.termsQuery("client_geoip.country_code2.keyword", filter.countries);
                query.must(countriesQuery);
            }

            log.debug("query is {}", query);

            // >>> Perform search
            Query searchQuery = new NativeSearchQueryBuilder()
                .withPageable(PageRequest.of(0, max_elastic_page_size))
                .withQuery(query)
                .withSourceFilter(new FetchSourceFilter(
                            new String[]{
                                "@timestamp",
                                "headers.x_forwarded_for", // Connector ID field
                                "verb",
                                "request",
                                "headers.content_length",
                                "response",
                                "bytes",
                                "client_geoip.ip",
                                "user_agent.os",
                                "user_agent.name",
                                "client_geoip.country_code2",
                                "client_geoip.city_name"
                            },
                            new String[]{}))
                .build();

            // Make sure we ask for logs where these fields exist
            query.must(QueryBuilders.existsQuery("headers"))
                 .must(QueryBuilders.existsQuery("user_agent"))
                 .must(QueryBuilders.existsQuery("client_geoip"));

            SearchHits<AdvancedFilteringResult> hits =
                elasticsearchOperations
                .search(searchQuery,
                        AdvancedFilteringResult.class,
                        IndexCoordinates.of(index_wild));
            log.debug("hits: {}", hits.toString());

            advancedFilteringResults = hits.stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
            // log.debug("advancedFilteringResults: {}", advancedFilteringResults.toString());
        }
        catch (Exception e) {
            log.error(e.toString());
        }

        return advancedFilteringResults;
    }

    /**
     * Internal Function that gets the timestamp from elastic and transforms it in a human readable
     * format to show to the frontend
     *
     * @param orig - original string
     * @return String - returned formatted string
     */
    private String getFormattedString(String orig) {

        String retStr = "";
        try {
            SimpleDateFormat sdfIN = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            sdfIN.setTimeZone(TimeZone.getTimeZone("UTC")); // elastic always returns in UTC
            SimpleDateFormat sdfOUT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Date date = sdfIN.parse(orig);
            retStr = sdfOUT.format(date);

        } catch (ParseException e) {
            log.error("Could not parse string: {}", orig);
            return retStr;
        }
        return retStr;
    }
}
