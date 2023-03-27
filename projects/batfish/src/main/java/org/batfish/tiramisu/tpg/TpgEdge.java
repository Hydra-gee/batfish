package org.batfish.tiramisu.tpg;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.batfish.tiramisu.NodeType;

@Data
@AllArgsConstructor
public class TpgEdge {
    TpgNode src;
    TpgNode dst;
    NodeType type;
    EdgeCost edgeCost;

    @Override
    public int hashCode(){
        return src.hashCode() + dst.hashCode();
    }

    @Override
    public boolean equals(Object o){
        if(!(o instanceof TpgEdge))
        {
            return false;
        }
        return this.hashCode() == o.hashCode();
    }
}

