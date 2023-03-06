package org.batfish.tiramisu.tpg;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TpgEdge {
    TpgNode src;
    TpgNode dst;
}

