package dev.mymoonx.moonotesnetwork;

public final class CatalogRefreshPolicyCheck {
    public static void main(String[] args) {
        long now=1730000000000L,day=CatalogRefreshPolicy.INTERVAL_MS;
        check(CatalogRefreshPolicy.due(0,now),"first launch");
        check(!CatalogRefreshPolicy.due(now,now),"no repeated queries same day");
        check(!CatalogRefreshPolicy.due(now-day+1,now),"before day boundary");
        check(CatalogRefreshPolicy.due(now-day,now),"daily refresh");
        check(!CatalogRefreshPolicy.due(now+10_000L,now),"small clock skew");
        check(CatalogRefreshPolicy.due(now+2*day,now),"clock correction");
        System.out.println("CatalogRefreshPolicyCheck: 6 cases passed");
    }
    private static void check(boolean result,String name){if(!result)throw new AssertionError(name);}
}
