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

public class ilpPathLength {

  Tpg g;
  GRBEnv env;
  GRBModel model;
  double obj= -1;

  public ilpPathLength(Tpg graph)
  { 
    g = graph;
    try {
      env = new GRBEnv("ilpPathLength.log");
      model = new GRBModel(env);
    } catch (GRBException e) {
      System.out.println("Error code at Constructor: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }      
  }

  public double returnObj() {
    return obj;
  }

  public void reset() {
    try {
      env = new GRBEnv("ilpPathLength.log");
      model = new GRBModel(env);
    } catch (GRBException e) {
      System.out.println("Error code at Constructor: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }      
  }
  
  public void formulate(TpgNode src, TpgNode dst, int length) {
    try {
      
      // Create variables
      GRBLinExpr expr, inflow, outflow;
      Map<TpgNode, Map<TpgNode, GRBVar>> flow = new HashMap<>();

      List<GRBVar> allFlows = new ArrayList<>();
      Map<TpgNode, Map<String, GRBVar>> community = new HashMap<>();

      int constraint = 0;
      for (TpgNode from : g.selectNodes(null,null,null,null)) {
        Map<TpgNode, GRBVar> flowTemp = new HashMap<>();
        for(TpgEdge to : from.getOutEdges()) {
          GRBVar flowVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY, ("flow"+from+"-"+to.getDst()) );
          flowTemp.put(to.getDst(), flowVar);
          if (from.getVlanType()== NodeType.VLAN_OUT && to.getDst().getVlanType()==NodeType.VLAN_IN) {
            allFlows.add(flowVar);
          }
        }
        flow.put(from, flowTemp);
      }

      for (TpgNode v : g.selectNodes(null,null,null,null)) {
        outflow = new GRBLinExpr(); 
        inflow = new GRBLinExpr();
        inflow.addConstant(0.0);
        outflow.addConstant(0.0);
        for (TpgEdge inEdge : v.getInEdges()) {
          TpgNode from = inEdge.getSrc();
          GRBVar flowcons = flow.get(from).get(v);
          inflow.addTerm(1.0, flowcons);
        }
        for (TpgEdge outEdge : v.getOutEdges()) {
          TpgNode to = outEdge.getDst();
          GRBVar flowcons = flow.get(v).get(to);
          outflow.addTerm(1.0, flowcons);
        }

        if (v == src) {
          model.addConstr(outflow, GRB.EQUAL, 1.0, "flowcons"+constraint);
          constraint = constraint + 1;                    
        } else if (v == dst) {
          model.addConstr(inflow, GRB.EQUAL, 1.0, "flowcons"+constraint);
          constraint = constraint + 1;                    
        } else {
          model.addConstr(outflow, GRB.EQUAL, inflow, "flowcons"+constraint);
          constraint = constraint + 1;          
        }

      }

      // Set objective:
      expr = new GRBLinExpr();
      for (GRBVar v : allFlows) {
        expr.addTerm(1.0, v);
      }
      if (length == 0) //min length
        model.setObjective(expr, GRB.MINIMIZE);
      else
        model.setObjective(expr, GRB.MAXIMIZE);

    } catch (GRBException e) {
      System.out.println("Error code at formulation: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }
  }

  public double run(){
    double time = 0;
     try {
        model.set(GRB.IntParam.OutputFlag, 0);
        //model.write("out.rlp");
         // Optimize model
        model.optimize();
        /*
        System.out.println(z.get(GRB.StringAttr.VarName)
                           + " " +z.get(GRB.DoubleAttr.X));
        */

        if(model.get(GRB.IntAttr.Status) == GRB.Status.INFEASIBLE){
            //System.out.println("There is no optimal solution "+ model.get(GRB.IntAttr.Status));
            //model.computeIIS();
            //model.write("model.ilp");

        } else {                          

          //System.out.println("Obj: " + model.get(GRB.DoubleAttr.ObjVal));
          obj = model.get(GRB.DoubleAttr.ObjVal);
          //time = model.get(GRB.DoubleAttr.Runtime) * 1000;
          //System.out.println("ILP Time: " + time + " ms");

          // Dispose of model and environment
          //model.write("out.sol");
        }
        //
        //System.out.println("Number of variables: " + model.get(GRB.IntAttr.NumVars));
        //System.out.println("Number of constraints: " + model.get(GRB.IntAttr.NumConstrs));
        model.dispose();
        env.dispose();
    } catch (GRBException e) {
      System.out.println("Error code at optimization: " + e.getErrorCode() + ". " +
                         e.getMessage());
    }
    return time;
  }

  public void boundLength() {
    formulate(g.getSrcNode(), g.getDstNode(), 1);
    run();
    System.out.println( "Max Length\t" + obj );
  }

  public void equalLength() {
    formulate(g.getSrcNode(), g.getDstNode(), 1);
    run();
    double min = returnObj();
    reset();
    formulate(g.getSrcNode(), g.getDstNode(), 0);
    run();
    double max = returnObj();
    //System.out.println( (time1 + time2)+ " ms" );
    System.out.println( "min length: " + min + "\tmax length: " + max );
  }
}