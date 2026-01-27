package com.extracrates.cutscene;

public record CutsceneSegmentRange(int startPoint, int endPoint) {
    public boolean matchesSegment(int segmentIndex) {
        return segmentIndex >= startPoint && segmentIndex < endPoint;
    }
}
