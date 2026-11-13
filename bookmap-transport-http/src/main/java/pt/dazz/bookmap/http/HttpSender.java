package pt.dazz.bookmap.http;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * Sends one HTTP request. The production sender calls {@code HttpClient.sendAsync}. The tests
 * use a fake sender to cause failures that a real socket does not cause in a reliable way.
 */
@FunctionalInterface
interface HttpSender {

    /**
     * Sends a request.
     *
     * @param request the request
     * @return the future response, with the full body
     */
    CompletableFuture<HttpResponse<byte[]>> send(HttpRequest request);
}
