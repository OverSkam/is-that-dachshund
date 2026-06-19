package overskam.projectH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationProject {

    private final List<AnnotationPolygon> polygons = new ArrayList<>();

    public void addPolygon(AnnotationPolygon polygon) {
        polygons.add(polygon);
    }

    public List<AnnotationPolygon> getPolygonsForFrame(int frameIndex) {
        List<AnnotationPolygon> result = new ArrayList<>();

        for (AnnotationPolygon polygon : polygons) {
            if (polygon.getFrameIndex() == frameIndex) {
                result.add(polygon);
            }
        }

        return result;
    }

    public boolean removeLastPolygonForFrame(int frameIndex) {
        for (int i = polygons.size() - 1; i >= 0; i--) {
            AnnotationPolygon polygon = polygons.get(i);

            if (polygon.getFrameIndex() == frameIndex) {
                polygons.remove(i);
                return true;
            }
        }

        return false;
    }

    public List<AnnotationPolygon> getAllPolygons() {
        return Collections.unmodifiableList(polygons);
    }


}