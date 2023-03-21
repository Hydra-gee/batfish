package org.batfish.tiramisu.verify.policy;

import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgEdge;
import org.batfish.tiramisu.tpg.TpgNode;

/**
 * @Author hydra
 * @create 2023/3/16 9:29
 */
public class BLOCK implements policyI {
  private Tpg g;

  public BLOCK(Tpg g){
    this.g = g;
  }
  @Override
  public boolean verify(){
    return TDFS(g.getSrcNode());
  }

  public boolean TDFS(TpgNode node){
    if(node == g.getDstNode()){
      return true;
    }
    for(TpgEdge e:node.getOutEdges()){
      if(TDFS(e.getDst())){
        return true;
      }
    }
    return false;
  }

}
