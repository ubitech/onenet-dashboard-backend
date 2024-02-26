# Onenet Wiki

This WIKI is relevant to the people involved in the project development, it contains technical information

## Authentication

To be completed.

## Connector http transactions live monitoring

The feature summary is: We need to monitor transactions made by the remote connectors of OneNet

How the remote log collection is set up, is explained in https://github.com/ubitech/onenet-dashboard-deployment/-/blob/master/WIKI.md
where elastic stack is used and logstash to logstash communication.

The live data reload is done via Server Sent Events with WebFlux considering the newest Spring Web library Reactor.

To develop on the feature locally, use instructions in README.md.

The whole situation would be simpler if we could use Kafka but this could not be done due to the company's infrastructure limitations.

### Flow

1. At the remote connector, a user action creates a log
2. We assume each log is a http transaction
3. Filebeat collects the log and sends it to connector's Logstash
4. The remote Logstash sends to Ubitech Logstash via http plugins
5. The log is inserted into Elastic via elastic output plugin
6. The backend queries the elastic perodically and sends the results to frontend


### Notes

- A lot of information is included in the code comments at `NetworkMonitoringService`
- All requests are done at `NetworkMonitoringService.java` via spring-data elasticsearch with `RestHighLevelClient` and `NativeQuery`
- `date_histogram` and `search count` is used for getting the data, grouping it and returning it ready, utilizing all optimizations
by elastic and performing zero post processing.

An example final query for the `query24hourEntriesCount` is as below, returning all data fully ready

```json
GET connectors-*/_search
{
  "query": {
    "range": {
      "@timestamp": {
        "from": "2022-08-03T14:00:00Z",
        "to": "2022-08-04T14:19:03Z",
        "include_lower": true,
        "include_upper": true,
        "boost": 1
      }
    }
  },
  "aggregations": {
    "query_per_hour": {
      "date_histogram": {
        "field": "@timestamp",
        "missing": 0,
        "fixed_interval": "1h",
        "offset": 0,
        "order": {
          "_key": "asc"
        },
        "keyed": false,
        "min_doc_count": 0,
        "extended_bounds": {
          "min": "2022-08-03T14:00:00Z",
          "max": "2022-08-04T14:19:03Z"
        }
      }
    }
  }
}
```

### Troubleshooting

Probable problems might be related to `timestamp` format or in general field names, or formats.

Any change in the elastic stack and especially in the Logstash, must be reflected in the backend.

If the refresh of events is not triggered then probably there is a problem with the logstash output that is used as trigger.

This may be in development or production.

## API Rate Limiting

There is a limit to how many requests a user can make. If a user makes an API call but doesn't have any available requests, the call will be rejected.

The service implementing this feature is in the `RateLimitngService.java` file. Also, the `RateLimitInterceptor` was added in the `WebMvcConfig.java` file to check if a new request can be answered.

### Flow

1. When the user makes his first API request of the session a "filled bucket" is formed relative to his auth token, with X avaiable requests. His available requests cannot exceed X.
2. The buckets of all the auth tokens are stored in a hashmap.
3. Whenever the user succesfully makes an API call, X is reduced by one.
4. Every Z minutes , X is increased by Y (bucket refill).
5. X (capacity), Y (token-refill) and Z (refill-interval-in-minutes) can be configured in the `application.yml` file.
6. If the user makes an API request, but has no requests available (the bucket is empty), the request is declined.
7. There is also a cleanup mechanism. The bucket hashmap resets every day at 4AM UTC to prevent high search times and overflowing.

These features are implemented in `RateLimitingService.java` and `RateLimitInterceptor.java`.
