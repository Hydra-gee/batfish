package org.batfish.tiramisu.verify;

import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.verify.model.TDFS.TDFS;

/**
 * @Author hydra
 * @create 2023/3/22 13:31
 */
public class PolicyVerify {
  Tpg g;
  public PolicyVerify(Tpg g){
    this.g = g;
  }
  public void verify(String policy){
    TDFS tdfs = new TDFS(g);
    boolean result = false;
    switch (policy){
      case "BLOCK":
        result = tdfs.TDFS_verify("");
        break;
      case "WAYPOINT":
        result = tdfs.TDFS_verify("d");
        break;
    default:
      System.out.println("NO POLICY FOUND");
      break;
    }
    System.out.println("result:"+result);
  }
}
