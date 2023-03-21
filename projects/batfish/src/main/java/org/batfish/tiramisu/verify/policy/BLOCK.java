package org.batfish.tiramisu.verify.policy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgEdge;
import org.batfish.tiramisu.tpg.TpgNode;

/**
 * @Author hydra
 * @create 2023/3/16 9:29
 */
public class BLOCK implements policyI {
  private Tpg g;
  private static Set<TpgNode> visited;

  public BLOCK(Tpg g){
    this.g = g;
  }

  @Override
  public boolean verify(){
    visited = new HashSet<>();
    return !TDFS(g.getSrcNode());
  }

  public boolean TDFS(TpgNode node){
    visited.add(node);
    if(node == g.getDstNode()){
      return true;
    }
    for(TpgEdge e:node.getOutEdges()){
      if(!visited.contains(e.getDst()) && TDFS(e.getDst())){
        return true;
      }
    }
    return false;
  }

}
