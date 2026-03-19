package uk.ac.ed.acp4.sample;

/**
 * Sample bank PaymentService - contains a timeout configuration bug.
 * Gateway timeout is too low causing frequent failures under load.
 * This file is read by the AI agent to generate fix suggestions.
 */
public class PaymentService {

    // BUG: timeout is only 1000ms — too low for payment gateway round trips
    private static final int GATEWAY_TIMEOUT_MS = 1000;
    private static final int MAX_RETRIES = 3;

    public PaymentResult processPayment(String txId, String userId, double amount) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                attempt++;
                return callPaymentGateway(txId, amount, GATEWAY_TIMEOUT_MS);
            } catch (PaymentGatewayTimeoutException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new RuntimeException(
                            "Payment " + txId + " permanently failed for user " + userId +
                                    " after max retries"
                    );
                }
                System.err.println("Retrying payment " + txId + " - attempt " + (attempt + 1) + " of " + MAX_RETRIES);
            }
        }
        throw new RuntimeException("Unexpected retry loop exit for tx: " + txId);
    }

    private PaymentResult callPaymentGateway(String txId, double amount, int timeoutMs) {
        // Simulated gateway call - throws if gateway doesn't respond in time
        throw new PaymentGatewayTimeoutException(
                "No response from gateway after " + timeoutMs + "ms for tx " + txId
        );
    }

    public static class PaymentGatewayTimeoutException extends RuntimeException {
        public PaymentGatewayTimeoutException(String message) {
            super(message);
        }
    }

    public static class PaymentResult {
        private final String txId;
        private final String status;
        public PaymentResult(String txId, String status) {
            this.txId = txId;
            this.status = status;
        }
    }
}