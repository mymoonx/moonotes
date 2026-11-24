package dev.mymoonx.moonotesnetwork;

/** Keeps the wheel's page title and indicator from repeating the same text. */
record WheelCenterLabels(String title, String subtitle) {
    static WheelCenterLabels of(String pageTitle, int pageNumber, int pageCount,
                                String hoveredName, String feedback) {
        String page = pageTitle == null ? "" : pageTitle;
        String selected = feedback != null && !feedback.isBlank() ? feedback
            : hoveredName != null && !hoveredName.isBlank() ? hoveredName : page;
        boolean showingPage = selected.equals(page);
        String count = pageCount > 1 ? Math.clamp(pageNumber, 1, pageCount) + "/" + pageCount : "";
        String subtitle = showingPage ? count : page + (count.isEmpty() ? "" : " · " + count);
        return new WheelCenterLabels(selected, subtitle);
    }
}
