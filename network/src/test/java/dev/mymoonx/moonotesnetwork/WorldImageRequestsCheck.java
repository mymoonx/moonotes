package dev.mymoonx.moonotesnetwork;

/** Missing world images cannot issue unbounded requests while the camera moves. */
public final class WorldImageRequestsCheck {
    private static final long SECOND = 1_000_000_000L;
    public static void main(String[] args) {
        var budget = new WorldImageRequests.Budget();
        require(budget.acquire("42", 0), "first request");
        require(budget.acquire("67", 0), "second request");
        require(!budget.acquire("third", 0), "two requests per rolling second");
        require(!budget.acquire("third", SECOND - 1), "no fixed-window boundary burst");
        require(budget.acquire("third", SECOND), "window expires");
        require(!budget.acquire("42", SECOND), "same image cannot retry early");
        require(!budget.acquire("42", 5 * SECOND - 1), "full five-second retry delay");
        require(budget.acquire("42", 5 * SECOND), "retry after five seconds");
        budget.reset();
        require(budget.tracked() == 0, "disconnect clears timestamps");
        require(budget.acquire("42", 0), "new world has a fresh budget");
        budget.reset();
        for (int i = 0; i < 200; i++) {
            require(budget.acquire("id-" + i, i * SECOND), "new images continue after old entries expire");
            require(budget.tracked() <= 64, "bounded identity tracking");
        }
        System.out.println("WorldImageRequestsCheck: PASS");
    }
    private static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
