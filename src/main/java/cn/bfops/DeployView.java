package cn.bfops;

import java.util.List;

/**
 * Where the deploy camera sits so the whole battle area is visible from above.
 *
 * <p>The requirement is a fixed viewpoint: when the deployment screen opens, the player looks down on
 * their area from high enough that the whole thing is in view, rather than from wherever their body
 * happens to be.
 *
 * <h2>The real constraint</h2>
 *
 * <p>A chunk is only rendered within the view distance of the camera, measured as straight-line
 * distance in three dimensions. For a camera sitting directly above the centre of an area of radius
 * {@code r} at height {@code h}, the <em>farthest</em> point of that area is at the corner of the
 * region, which is {@code sqrt(r² + h²)} away. So the whole area is visible exactly when
 *
 * <pre>sqrt(r² + h²) &lt;= view</pre>
 *
 * <p>which means the height must be <b>low</b>, not high: {@code h = sqrt(view² - r²)}. This is the
 * opposite of the intuition that a bigger area needs a higher camera. Climbing higher does not let you
 * see more — it pushes the far edge past the fog limit and the ground dissolves entirely. Given a fixed
 * view distance, more height means less map.
 *
 * <p>The consequence to state plainly: an area whose radius exceeds the view distance cannot be framed
 * from above at all, at any height. {@link #fits} and {@link #warning} report that rather than quietly
 * showing a cropped or fogged-out map, and {@link #fits} accounts for the minimum height as well, since
 * even {@link #MIN_Y} adds distance.
 *
 * <h2>Why straight down</h2>
 *
 * <p>The pitch is -90: looking vertically down. Any tilt foreshortens the area into a trapezoid, so part
 * of it occupies fewer pixels than the rest, and the operator's mental map of "where is that flag"
 * stops matching the screen. A top-down view is the only orientation in which the area's ground plan
 * maps onto the screen undistorted, which is what a deployment map is for.
 */
final class DeployView {
    /** Looking straight down. Any tilt distorts the ground plan. */
    static final double PITCH = 90.0;
    /** Never put the camera below this above the area, however small the area is. */
    static final double MIN_Y = 40.0;
    /** Hard ceiling, well past any sane view distance; the view-distance cap is what actually binds. */
    static final double MAX_Y = 512.0;
    /** Vanilla's default render distance in blocks; the caller may pass a server-configured one. */
    static final int DEFAULT_VIEW = 100;

    /**
     * A camera placement: eye position and the pitch to look at.
     *
     * @param fits      true when the whole area is within the view distance
     * @param overflow  how many blocks past the view distance the farthest point is, 0 when it fits
     * @param headroom  the height above ground, after clamping
     */
    record Placement(double x, double y, double z, float yaw, float pitch,
                     boolean fits, double overflow, double headroom) {
    }

    private DeployView() { }

    /**
     * Places the camera above the centre of an area.
     *
     * @param centreX centre of the area
     * @param centreZ centre of the area
     * @param span    width of the area in blocks, i.e. the larger of its two horizontal extents
     * @param groundY ground height at the centre, used as the base for the height
     * @param view    view distance in blocks
     */
    static Placement place(double centreX, double centreZ, double span, double groundY, int view) {
        int distance = view <= 0 ? DEFAULT_VIEW : view;
        double radius = Math.max(0, span) / 2.0;

        // h = sqrt(view^2 - r^2), so the farthest corner sits exactly at the view limit. When the area is
        // already wider than the view there is no usable height and the float is clamped to MIN_Y.
        double ideal = radius >= distance ? 0 : Math.sqrt(distance * distance - radius * radius);
        double height = Math.max(MIN_Y, Math.min(MAX_Y, ideal));
        double eyeY = groundY + height;

        double farthest = Math.hypot(radius, height);
        boolean fits = farthest <= distance;
        double overflow = fits ? 0 : farthest - distance;
        return new Placement(centreX, eyeY, centreZ, 0f, (float) -PITCH, fits, overflow, height);
    }

    /**
     * True when an area of this span can be seen whole from the given view distance.
     *
     * <p>Accounts for {@link #MIN_Y}: a camera cannot be flush with the ground, so an area slightly
     * narrower than the view distance still fails once the minimum height is added in.
     */
    static boolean fitsHorizontally(double span, int view) {
        int distance = view <= 0 ? DEFAULT_VIEW : view;
        return Math.hypot(Math.max(0, span) / 2.0, MIN_Y) <= distance;
    }

    /** Human-readable warning for an area that is too wide, or {@code null} when it fits. */
    static String warning(double span, int view) {
        if (fitsHorizontally(span, view)) return null;
        int distance = view <= 0 ? DEFAULT_VIEW : view;
        double radius = Math.max(0, span) / 2.0;
        // The usable span: the widest area that still fits once the camera's minimum height is counted.
        double usable = 2 * Math.sqrt(Math.max(0, distance * distance - MIN_Y * MIN_Y));
        return "活动区域宽 " + Math.round(span) + " 格，视距只有 " + distance
                + " 格，无法完整显示（可完整显示的最大宽度约 " + Math.round(usable)
                + " 格）；请缩小区域或提高视距";
    }

    /**
     * The largest span that can be framed whole at this view distance, in blocks.
     *
     * <p>Used by the editor to warn while drawing rather than only at deploy time, which is when it is
     * still cheap to fix.
     */
    static double maxSpan(int view) {
        int distance = view <= 0 ? DEFAULT_VIEW : view;
        return 2 * Math.sqrt(Math.max(0, distance * distance - MIN_Y * MIN_Y));
    }

    /** Convenience for the editor: the span of a drawn polygon, in blocks. */
    static double span(List<Model.Vec> polygon, Model.Arena map) {
        if (polygon == null || polygon.size() < 3 || map == null) return 0;
        return map.extent(polygon)[2];
    }
}
