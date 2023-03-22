package org.batfish.tiramisu.verify.model.TDFS;

import java.util.HashSet;
import java.util.Set;
import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgEdge;
import org.batfish.tiramisu.tpg.TpgNode;

/**
 * @Author hydra
 * @create 2023/3/22 10:58
 */
public class TDFS {
  private Tpg g;
  private static Set<TpgNode> visited;

  public TDFS(Tpg g){
    this.g = g;
  }

  public boolean TDFS_verify(String waypoint){
    visited = new HashSet<>();
    return !DFS(g.getSrcNode(),waypoint);
  }

  public boolean DFS(TpgNode node,String waypoint){
    visited.add(node);
    if(node == g.getDstNode()){
      return true;
    }
    for(TpgEdge e:node.getOutEdges()){
      if(!visited.contains(e.getDst()) && !e.getDst().getDeviceId().equals(waypoint) && DFS(e.getDst(),waypoint)){
        return true;
      }
    }
    return false;
  }
}
