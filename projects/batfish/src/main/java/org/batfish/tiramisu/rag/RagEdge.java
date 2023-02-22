package org.batfish.tiramisu.rag;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RagEdge {
    private RagNode src;
    private RagNode dst;
    private boolean isIbgp;
}

