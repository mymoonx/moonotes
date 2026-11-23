package dev.mymoonx.moonotesnetwork;

public final class ScrollMathCheck {
    private static void eq(int actual,int expected,String message) {
        if(actual!=expected)throw new AssertionError(message+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        eq(ScrollMath.thumbHeight(204,6,0),204,"No-scroll thumb");
        eq(ScrollMath.thumbHeight(204,6,50),21,"Visible thumb");
        eq(ScrollMath.thumbOffset(204,21,0,50),0,"Top offset");
        eq(ScrollMath.thumbOffset(204,21,50,50),183,"Bottom offset");
        eq(ScrollMath.rowForMouse(119+10.5,119,204,6,50,10.5),0,"Grab at top");
        eq(ScrollMath.rowForMouse(119+183+10.5,119,204,6,50,10.5),50,"Drag to bottom");
        eq(ScrollMath.rowForMouse(-10000,119,204,6,50,10.5),0,"Clamp before track");
        eq(ScrollMath.rowForMouse(10000,119,204,6,50,10.5),50,"Clamp beyond track");
        eq(ScrollMath.rowForMouse(0,0,0,0,0,0),0,"Zero-size");
        System.out.println("ScrollMath: 9 checks OK");
    }
}
