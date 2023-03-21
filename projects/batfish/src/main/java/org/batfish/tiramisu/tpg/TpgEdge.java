package org.batfish.tiramisu.tpg;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TpgEdge {
    TpgNode src;
    TpgNode dst;

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

