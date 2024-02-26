package eu.ubitech.onenet.interceptors;

import eu.ubitech.onenet.service.RateLimitingService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

@Component
// Checks if the client has available API calls to make before making a new one
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String HEADER_API_KEY = "Authorization";
    private static final String HEADER_LIMIT_REMAINING = "X-Rate-Limit-Remaining";
    private static final String HEADER_RETRY_AFTER = "X-Rate-Limit-Retry-After-Seconds";

    @Autowired
    private RateLimitingService rateLimitingService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String apiKey = request.getHeader(HEADER_API_KEY);

        if (apiKey == null || apiKey.isEmpty()) {
            return true;
        }

        Bucket tokenBucket = rateLimitingService.resolveBucket(apiKey);
        ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(1);

        // if the user has API requests available, fulfil the request
        if (probe.isConsumed()) {
            response.addHeader(HEADER_LIMIT_REMAINING, String.valueOf(probe.getRemainingTokens()));
            return true;
        }
        // if the user has no API requests available, send an error message
        else {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader(HEADER_RETRY_AFTER, String.valueOf(waitForRefill));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "You have exhausted your API Request Quota"); // 429
            return false;
        }
    }
}