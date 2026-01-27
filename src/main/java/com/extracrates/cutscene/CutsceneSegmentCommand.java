package com.extracrates.cutscene;

import java.util.List;

public record CutsceneSegmentCommand(int startPoint, int endPoint, List<String> commands) {
    public boolean matchesSegment(int segmentIndex) {
        return segmentIndex >= startPoint && segmentIndex < endPoint;
    }
}
