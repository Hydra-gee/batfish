package org.batfish.tiramisu.verify.model.ILP;

import gurobi.GRB;
import gurobi.GRBEnv;
import gurobi.GRBException;
import gurobi.GRBLinExpr;
import gurobi.GRBModel;
import gurobi.GRBVar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.batfish.tiramisu.NodeType;
import org.batfish.tiramisu.tpg.Tpg;
import org.batfish.tiramisu.tpg.TpgEdge;
import org.batfish.tiramisu.tpg.TpgNode;

public class ilpMinCut {

  Tpg g;
  GRBEnv env;
  GRBModel model;
  public double runTime = 0;
  public double obj = -1;

  public ilpMinCut(Tpg g)
  { 
    this.g = g;
    //初始化GRB环境
    try {
      env = new GRBEnv("kConnected.log");
      model = new GRBModel(env);
    } catch (GRBException e) {
      System.out.println("Error code at Constructor: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }      
  }

  public double returnTime() {
    return runTime;
  }
  
  public double returnObj() {
    return obj;
  }
  
  public void formulate(TpgNode src, TpgNode dst) {
    try {
      // Create variables
      GRBLinExpr expr, inflow, tempAdd, temp1;
      Map<TpgNode, Map<TpgNode, GRBVar>> flow = new HashMap<>();
      Map<TpgEdge, GRBVar> fail = new HashMap<>();
      List<GRBVar> allFail = new ArrayList<>();
      Map<TpgNode, GRBVar> reachable = new HashMap<>();

      int constraint = 0;
      for (TpgNode from : g.selectNodes(null,null,null,null)) {
        Map<TpgNode, GRBVar> flowTemp = new HashMap<>();
        for(TpgEdge to : from.getOutEdges()) {
          GRBVar flowVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "flow"+from+"-"+to.getDst() );
          flowTemp.put(to.getDst(), flowVar);
        }
        flow.put(from, flowTemp);
      }

      for (TpgNode v : g.selectNodes(null,null,null,null)) {
        GRBVar reach = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, ("reach"+v));
        reachable.put(v, reach);
      }

      for(TpgNode fromNode:g.selectNodes(null,null, NodeType.VLAN_OUT,null)){
        for(TpgEdge e:fromNode.getOutEdges()){
          TpgNode toNode = e.getDst();
          if(toNode.getVlanType() == NodeType.VLAN_IN){
            GRBVar failKey = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, ("fail"+fromNode.getDeviceId()+"-"+toNode.getDeviceId()));
            allFail.add(failKey);
//            for (TpgEdge tpgEdge : valueEdgeSet) {
            fail.put(e, failKey);
//            }
          }
        }
      }

      model.addConstr(reachable.get(src), GRB.EQUAL, 1.0, "reachcons"+constraint);
      constraint = constraint + 1;
      model.addConstr(reachable.get(dst), GRB.EQUAL, 0.0, "reachcons"+constraint);
      constraint = constraint + 1;

      for (TpgNode v : g.selectNodes(null,null,null,null)) {
        if (v == src) {
          continue;
        }

        inflow = new GRBLinExpr();

        // flow = reach - fail
        // reach = summation(flow)
        for (TpgEdge inEdge : v.getInEdges()) {
          TpgNode from = inEdge.getSrc();
          GRBVar flowcons = flow.get(from).get(v);
          temp1 = new GRBLinExpr();
          temp1.addTerm(1.0, reachable.get(from));
          if (fail.containsKey(inEdge)) {
            temp1.addTerm(-1.0, fail.get(inEdge));
          }

          model.addConstr(flowcons, GRB.GREATER_EQUAL, temp1, "flowcons"+constraint);
          constraint = constraint + 1;

          
          inflow.addTerm(1.0, flowcons);
          model.addConstr(reachable.get(v), GRB.GREATER_EQUAL, flowcons, "reach-or-cons"+constraint);
          constraint = constraint + 1;
        }


        model.addConstr(reachable.get(v), GRB.LESS_EQUAL, inflow, "flowcons"+constraint);
        constraint = constraint + 1;

      }
      

      // Set objective:
      expr = new GRBLinExpr();
      for (GRBVar v : allFail) {
        expr.addTerm(1.0, v);
      }
      model.setObjective(expr, GRB.MINIMIZE);

    } catch (GRBException e) {
      System.out.println("Error code at formulation: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }
  }

  public void run(){
     try {
        model.set(GRB.IntParam.OutputFlag, 0);
        //model.write("out.rlp");
         // Optimize model
        model.optimize();

        if(model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE){
            System.out.println("There is no optimal solution "+ model.get(GRB.IntAttr.Status));
            //model.computeIIS();
            //model.write("model.ilp");

        } else {                          

          //System.out.println("Obj: " + model.get(GRB.DoubleAttr.ObjVal));
          
          obj = model.get(GRB.DoubleAttr.ObjVal);
          /*time = model.get(GRB.DoubleAttr.Runtime) * 1000;
          runTime = time;*/
          //System.out.println("ILP Time: " + time + " ms");

          // Dispose of model and environment
          //model.write("out.sol");
        }

        //System.out.println("Number of variables: " + model.get(GRB.IntAttr.NumVars));
        //System.out.println("Number of constraints: " + model.get(GRB.IntAttr.NumConstrs));
        //
        model.dispose();
        env.dispose();
    } catch (GRBException e) {
      System.out.println("Error code at optimization: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }
  }

  public double fail() {
    formulate(g.getSrcNode(), g.getDstNode());
    run();
    System.out.println("Failures to disconnect the graph " + Math.ceil(obj));
    return 0;
  }
}