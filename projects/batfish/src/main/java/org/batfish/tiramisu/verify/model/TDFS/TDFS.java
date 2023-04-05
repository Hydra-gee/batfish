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

  //检验src到dst是否有通路，若有返回true
  public boolean verifyReach(TpgNode src,TpgNode dst){
    visited = new HashSet<>();
    return DFS(src,dst);
  }

  //深度搜索递归函数
  public boolean DFS(TpgNode thisNode,TpgNode dst){
    if(!thisNode.isValid()){
      return false;
    }
    visited.add(thisNode);
    if(thisNode == g.getDstNode()){
      return true;
    }
    for(TpgEdge e:thisNode.getOutEdges()){
      if(!visited.contains(e.getDst()) && DFS(e.getDst(),dst)){
        return true;
      }
    }
    return false;
  }

  public boolean alwaysBlocked(TpgNode src,TpgNode dst) {
    //无src或dst,异常情况
    if (src==null || dst==null){
      return true;
    }
    //若src到dst无通路，则一定阻塞
    if (verifyReach(src,dst)) {
      return true;
    }
    //src到dst有通路
    //若无community tag,有通路则会连通
    if ((g.communityBlocked.size()==0)|| g.communityAdded.isEmpty()){
      return false;
    }
    //检查community tag 引起的阻塞
    Set <TpgNode>RemovedNodes = new HashSet<>();
    boolean mayReach = false;

    for (String comm : g.communityBlocked) {
      for (TpgNode blockNode : g.communityBlockNodes.get(comm)) {
        blockNode.setValid(false);
        RemovedNodes.add(blockNode);
      }

      if (verifyReach(src,dst)) {
        for(TpgNode n:RemovedNodes){
          n.setValid(true);
        }
        RemovedNodes.clear();
        continue;
      }
      // this means all routes from src to dst goes through blocking-node on community
      for(TpgNode n:RemovedNodes){
        n.setValid(true);
      }
      RemovedNodes.clear();
      if (g.communityAdded.containsKey(comm)) {
        //System.out.println("Checking with added node removed" + g.communityAdded.get(comm));
        for (TpgNode addedNode : g.communityAdded.get(comm)) {
          addedNode.setValid(false);
          RemovedNodes.add(addedNode);
        }
      } else {
        // no one is adding this community, so ignore
        continue;
      }
      if (verifyReach(g.getSrcNode(),g.getDstNode())) {
        for(TpgNode n:RemovedNodes){
          n.setValid(true);
        }
        RemovedNodes.clear();
        continue;
      }
      // this means all routes from src to dst goes through both nodes adding and blocking on community

      //g.setNeighborMap(copyMap);

      mayReach = false;
      for (TpgNode blockNode : g.communityBlockNodes.get(comm)) {
        if (verifyReach(blockNode,dst)) {
          mayReach = true;
        }
      }
      for(TpgNode n:RemovedNodes){
        n.setValid(true);
      }
      RemovedNodes.clear();
      if (mayReach) {
        continue;
      }

      // after removing community-added nodes, block nodes can't reach destination

      // this means all routes from community-blocking-node to dst will go through community-adding-node
			/*
			if (g.communityRemoveNodes.containsKey(comm)) {
				System.out.println("Checking with removing node removed");
				for (Node removeNode : g.communityRemoveNodes.get(comm)) {
					removeNode(removeNode);
				}
			}

			//boolean reach = true;
			for (Node addedNode : g.communityAdded.get(comm)) {
				for (Node blockNode : g.communityBlockNodes.get(comm)) {
					if (!unreach.isUnreachable(addedNode, blockNode)) { //nodes can reach
						return false;
					}
				}
			}*/

      mayReach = false;
      for (TpgNode blockNode : g.communityBlockNodes.get(comm)) {
        if (g.communityRemoveNodes.containsKey(comm)) {
          for (TpgNode removeNode : g.communityRemoveNodes.get(comm)) {
            if (verifyReach(blockNode,g.getDstNode())) {
              for (TpgNode addedNode : g.communityAdded.get(comm)) {
                if (verifyReach(removeNode,addedNode)) {
                  mayReach = true;
                  break;
                }
              }
            }
          }
        }
      }
      for(TpgNode n:RemovedNodes){
        n.setValid(true);
      }
      RemovedNodes.clear();
      if (mayReach) {
        continue;
      }
      return true;
    }
    return false;
  }

  public boolean isWaypoint(TpgNode src,TpgNode dst,String waypoint){
    for(TpgNode n:g.selectNodes(waypoint,null,null,null)){
      n.setValid(false);
    }
    boolean result = alwaysBlocked(src,dst);
    for(TpgNode n:g.selectNodes(waypoint,null,null,null)){
      n.setValid(true);
    }
    return result;
  }

  public boolean WaypointChain(String[] waypoints){
    return true;
  }

  public boolean blackHole(){
    return true;
  }
}
