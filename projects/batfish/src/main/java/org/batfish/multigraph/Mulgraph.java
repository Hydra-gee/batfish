package org.batfish.mulgraph;

import static org.batfish.symbolic.CommunityVarCollector.collectCommunityVars;

import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.DeviceType;
import org.batfish.datamodel.IpWildcard;

import org.batfish.symbolic.Graph;
import org.batfish.symbolic.GraphEdge;
import org.batfish.symbolic.Protocol;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.SetWeight;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.SetAdministrativeCost;

import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.LineAction;

import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.Prefix;


import org.batfish.datamodel.routing_policy.statement.AddCommunity;
import org.batfish.datamodel.routing_policy.statement.DeleteCommunity;
import org.batfish.datamodel.routing_policy.statement.RetainCommunity;
import org.batfish.datamodel.routing_policy.statement.SetCommunity;
import org.batfish.datamodel.routing_policy.expr.MatchCommunitySet;
import org.batfish.datamodel.routing_policy.expr.CommunitySetExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.Disjunction;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.WithEnvironmentExpr;

import org.batfish.datamodel.CommunityList;
import org.batfish.datamodel.CommunityListLine;
import org.batfish.datamodel.StaticRoute;

import org.batfish.symbolic.AstVisitor;
import org.batfish.symbolic.CommunityVar;


public class Mulgraph {
	
	Graph g;
	Digraph dg;

	Map<String, List<Protocol>> _protocols;
	// Map of vertex-key to multilayer graph node 
	private Map<String, Node> multigraphNode;
    private Map<String, Set<Node>> phyNodeMap;
    private Map<String, Set<Edge>> phyEdgeMap;


    public Map<String, Long> _communityID;
    public Map<String, Set<String>> _actCommunity;
    public Map<String, Set<String>> _addCommunity;
    public Map<String, Set<String>> _removeCommunity;

    public Map<String, Map<String, EdgeCost>> _routerCommunityLp;

    public Map<String, Set<String>> _vrfMap;
    public Set<String> _allVrfMap;

    public Map<String, Map<String, Set<String>>> l2map;

    private Map<String, Map<String, Node>> _switchVLANMap;

    public Set<String> _hasIBGP;

    IpWildcard srcIp;
    IpWildcard dstIp;

    Node srcNode = null;
    Node dstNode = null;

    String srcName = null;
    String dstName = null;

    public Mulgraph(Graph g, IpWildcard src, IpWildcard dst) {//, String src, String dst){
    	this.g = g;
    	dg = new Digraph();

        srcIp = src;
        dstIp = dst;

    	multigraphNode = new HashMap<>();
    	phyNodeMap = new HashMap<>();
        phyEdgeMap = new HashMap<>();

        _actCommunity = new TreeMap<>();
        _addCommunity = new TreeMap<>();
        _removeCommunity = new TreeMap<>();
        _routerCommunityLp = new TreeMap<>();
        _vrfMap = new TreeMap<>();
        _allVrfMap = new HashSet<>();
        _switchVLANMap = new TreeMap<>();
        _hasIBGP = new HashSet<>();

    }

    public void setL2Map(Map<String, Map<String, Set<String>>> l2) {
        l2map = l2;
    }

    public Digraph getDigraph() {
    	return dg;
    }

    public void buildGraph() {
        buildVrfMap();
        buildRouterProtocol();
        buildCommunity();
    	buildNodes();
    	buildEdges();
        addDEFEdge();
        setNodes();
        System.out.println(dg);
        //System.out.println(_vrfMap);
    }

    public void setNodes() {
        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            String router = entry.getKey();
            Configuration conf = g.getConfigurations().get(router);
            for (Protocol proto : _protocols.get(router)) {
                Set<Prefix> prefixes = Graph.getOriginatedNetworks(conf, proto);
                System.out.println(router + "\t" + prefixes);
                for (Prefix pp : prefixes) {
                    if (pp.containsPrefix(srcIp.toPrefix())) {
                        srcName = router;
                    }
                    if (pp.containsPrefix(dstIp.toPrefix())) {
                        dstName = router;
                    }
                }
            }
        }

        if (srcName != null) {
            System.out.println(srcName);
            srcNode = new Node(srcName, protocol.SRC);
            dg.add(srcNode);
            Set<Node> allnode = phyNodeMap.get(srcName);
            for (Node anode : allnode) {
                EdgeCost ec = new EdgeCost();
                dg.add(srcNode, anode, ec, anode.getType());
            }
        }
        if (dstName != null) {
            System.out.println(dstName);
            dstNode = new Node(dstName, protocol.DST);
            dg.add(dstNode);
            Set<Node> allnode = phyNodeMap.get(dstName);
            for (Node anode : allnode) {
                EdgeCost ec = new EdgeCost();
                dg.add(anode, dstNode, ec, anode.getType());
            }

        }

    }

    public void addDEFEdge() {
        // add edge between ibgp and ospf
        for (String node : phyNodeMap.keySet()) {
            Set<Node> allnode = phyNodeMap.get(node);
            for (Node srcprot : allnode) {
                if (srcprot.getType() == protocol.OSPF) {
                    for (Node dstprot : allnode) {
                        if (dstprot.getType() == protocol.BGP) {
                            EdgeCost ec = new EdgeCost();
                            //System.out.println("Mayadd "+ srcprot +"\t"+ dstprot);
                            if (hasSameVrf(node, srcprot, dstprot))
                                dg.add(srcprot, dstprot, ec, protocol.DEF);
                            //connectVRF(node, node, srcprot.getId(), dstprot.getId(), ec, false, protocol.IBGP);
                        }
                    }
                }
            }
        }

    }

    public void buildVrfMap() {

        for (String router : g.getRouters()) {
            _vrfMap.put(router, new HashSet<>());
        }

        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            String router = entry.getKey();

            List<GraphEdge> edges = entry.getValue();
            for (GraphEdge e : edges) {
                boolean addedStart = false, addedEnd = false;
                String startVrfName = "", endVrfName = "";
                String peer = e.getPeer();
                if (e.getStart()!=null) {
                    startVrfName = e.getStart().getVrfName();
                    if (!(startVrfName.equals("default") || startVrfName.equals("Mgmt-intf"))) {
                        _allVrfMap.add(startVrfName);
                        addedStart = true;
                        _vrfMap.get(router).add(startVrfName);
                    }
                }
                if (e.getEnd()!=null) {
                    endVrfName = e.getEnd().getVrfName();
                    if (!(endVrfName.equals("default") || endVrfName.equals("Mgmt-intf"))) {
                        _allVrfMap.add(endVrfName);
                        addedEnd = true;
                        _vrfMap.get(peer).add(endVrfName);
                    }
                }
                if (peer!=null && addedStart == true && addedEnd == false) {
                    _vrfMap.get(peer).add(startVrfName);
                } else if (router!=null && addedEnd == true && addedStart == false) {
                    _vrfMap.get(router).add(endVrfName);
                }

            }
        }

        for (String router : g.getRouters()) {
            if (_vrfMap.get(router).size() == 0 && g.getConfigurations().get(router).getMPLS())
                _vrfMap.get(router).addAll(_allVrfMap);
        }

    }

    public boolean blockFilter(RoutingPolicy rp, Configuration conf) {
        if (rp == null)
            return false;
        for ( Statement st : rp.getStatements() ) {
            if ( st instanceof If ) {
                If i = (If) st;
                if (i.getGuard() instanceof MatchPrefixSet) {
                    MatchPrefixSet m = (MatchPrefixSet) i.getGuard();
                    if (m.getPrefixSet() instanceof NamedPrefixSet) {
                        NamedPrefixSet x = (NamedPrefixSet) m.getPrefixSet();
                        RouteFilterList fl = conf.getRouteFilterLists().get(x.getName());
                        if (fl != null) {
                            for ( RouteFilterLine line : fl.getLines() ) {
                                if (line.getAction() == LineAction.DENY && line.getIpWildcard().intersects(dstIp))
                                    return true;
                            }

                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean blockAcl(Interface out, Interface in) {
        //Interface i = ge.getStart();

        IpAccessList outbound = out.getOutgoingFilter();

        if (outbound != null) {
            for ( IpAccessListLine line : outbound.getLines() ) {
                if (line.getAction() == LineAction.DENY) {
                    if ( line.getMatchCondition() instanceof MatchHeaderSpace) {
                        MatchHeaderSpace mhs = (MatchHeaderSpace)line.getMatchCondition();

                        if (mhs.getHeaderspace().getSrcIps() instanceof IpWildcardIpSpace &&
                            mhs.getHeaderspace().getDstIps() instanceof IpWildcardIpSpace) {
                            IpWildcardIpSpace srcWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getSrcIps();
                            IpWildcardIpSpace dstWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getDstIps();
                            if (srcWildCard.getIpWildcard().intersects(srcIp) && 
                                dstWildCard.getIpWildcard().intersects(dstIp)) {
                                //System.out.println("Blocked by ACL " + mhs.getHeaderspace().getSrcIps() + 
                                //    "\t" + mhs.getHeaderspace().getDstIps());
                                return true;
                            }
                        }
                    }
                }
            }
        }

        //Interface i = ge.getEnd();
        IpAccessList inbound = in.getIncomingFilter();

        if (inbound != null) {
            for ( IpAccessListLine line : inbound.getLines() ) {
                if (line.getAction() == LineAction.DENY) {
                    if ( line.getMatchCondition() instanceof MatchHeaderSpace) {
                        MatchHeaderSpace mhs = (MatchHeaderSpace)line.getMatchCondition();

                        if (mhs.getHeaderspace().getSrcIps() instanceof IpWildcardIpSpace &&
                            mhs.getHeaderspace().getDstIps() instanceof IpWildcardIpSpace) {
                            IpWildcardIpSpace srcWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getSrcIps();
                            IpWildcardIpSpace dstWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getDstIps();
                            if (srcWildCard.getIpWildcard().intersects(srcIp) && 
                                dstWildCard.getIpWildcard().intersects(dstIp)) {
                                //System.out.println("Blocked by ACL " + mhs.getHeaderspace().getSrcIps() + 
                                //    "\t" + mhs.getHeaderspace().getDstIps());
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public boolean matches(RouteFilterList fl) {
        if (fl != null) {
            for ( RouteFilterLine line : fl.getLines() ) {
                if (line.getIpWildcard().intersects(dstIp))
                    return true;
            }
        }
        return false;
    }

    public void setBGPCost(RoutingPolicy importRP, RoutingPolicy exportRP, EdgeCost ec, Configuration conf) {
        setBGPCostPol(importRP, ec, conf);
        setBGPCostPol(exportRP, ec, conf);
    }

    public void setRouterCommunityCost(List<Statement> stmt, String router, String comm) {
        EdgeCost ec = new EdgeCost();
        boolean set = false;
        for (Statement st : stmt) {
            if ( st instanceof SetLocalPreference ) {
                SetLocalPreference i = (SetLocalPreference) st;
                if (i.getLocalPreference() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getLocalPreference();
                    ec.setLP(li.getValue());
                    set = true;
                }
            } else if ( st instanceof SetMetric ) {
                SetMetric i = (SetMetric) st;
                if (i.getMetric() instanceof LiteralLong) {
                    LiteralLong li = (LiteralLong) i.getMetric();
                    ec.setMED(((int)li.getValue()));
                    set = true;
                }
            }  else if ( st instanceof SetWeight ) {
                SetWeight i = (SetWeight) st;
                if (i.getWeight() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getWeight();
                    ec.setWeight(li.getValue());
                    set = true;
                }
            } else if ( st instanceof SetAdministrativeCost ) {
                SetAdministrativeCost i = (SetAdministrativeCost) st;
                if (i.getAdmin() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getAdmin();
                    ec.setAD(li.getValue());
                    set = true;
                }
            }
        }
        if (set)
            _routerCommunityLp.get(router).put(comm, ec);
    }



    public void setBGPCostPol(RoutingPolicy rp, EdgeCost ec, Configuration conf) {
        if (rp != null) {
            for (Statement st : rp.getStatements()) {
                if ( st instanceof SetLocalPreference ) {
                    SetLocalPreference i = (SetLocalPreference) st;
                    if (i.getLocalPreference() instanceof LiteralInt) {
                        LiteralInt li = (LiteralInt) i.getLocalPreference();
                        ec.setLP(li.getValue());
                    }
                } else if ( st instanceof SetMetric ) {
                    SetMetric i = (SetMetric) st;
                    if (i.getMetric() instanceof LiteralLong) {
                        LiteralLong li = (LiteralLong) i.getMetric();
                        ec.setMED(((int)li.getValue()));
                    }
                }  else if ( st instanceof SetWeight ) {
                    SetWeight i = (SetWeight) st;
                    if (i.getWeight() instanceof LiteralInt) {
                        LiteralInt li = (LiteralInt) i.getWeight();
                        ec.setWeight(li.getValue());
                    }
                } else if ( st instanceof SetAdministrativeCost ) {
                    SetAdministrativeCost i = (SetAdministrativeCost) st;
                    if (i.getAdmin() instanceof LiteralInt) {
                        LiteralInt li = (LiteralInt) i.getAdmin();
                        ec.setAD(li.getValue());
                    }
                } else if ( st instanceof If ) {
                    If i = (If) st;
                    //System.out.println(i.getGuard()+"\t"+i.getTrueStatements()+"\t"+i.getFalseStatements());
                    if (i.getGuard() instanceof MatchPrefixSet) {
                        MatchPrefixSet m = (MatchPrefixSet) i.getGuard();
                        if (m.getPrefixSet() instanceof NamedPrefixSet) {
                            NamedPrefixSet x = (NamedPrefixSet) m.getPrefixSet();
                            RouteFilterList fl = conf.getRouteFilterLists().get(x.getName());
                            if (matches(fl)) {
                                for ( Statement trueStmt : i.getTrueStatements()) {
                                    if ( trueStmt instanceof SetLocalPreference ) {
                                        SetLocalPreference lp = (SetLocalPreference) trueStmt;
                                        if (lp.getLocalPreference() instanceof LiteralInt) {
                                            LiteralInt li = (LiteralInt) lp.getLocalPreference();
                                            ec.setLP(li.getValue());
                                        }
                                    } else if ( trueStmt instanceof SetMetric ) {
                                        SetMetric lmetric = (SetMetric) trueStmt;
                                        if (lmetric.getMetric() instanceof LiteralLong) {
                                            LiteralLong li = (LiteralLong) lmetric.getMetric();
                                            ec.setMED(((int)li.getValue()));
                                        }
                                    }  else if ( trueStmt instanceof SetWeight ) {
                                        SetWeight lweight = (SetWeight) trueStmt;
                                        if (lweight.getWeight() instanceof LiteralInt) {
                                            LiteralInt li = (LiteralInt) lweight.getWeight();
                                            ec.setWeight(li.getValue());
                                        }
                                    }  else if ( trueStmt instanceof SetAdministrativeCost ) {
                                        SetAdministrativeCost lad = (SetAdministrativeCost) trueStmt;
                                        if (lad.getAdmin() instanceof LiteralInt) {
                                            LiteralInt li = (LiteralInt) lad.getAdmin();
                                            ec.setAD(li.getValue());
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }

        }
    }


    public void appliesRP(String router, List<Statement> stmts) {

        Configuration conf = g.getConfigurations().get(router);
        for (Statement stmt : stmts) {
            if (stmt instanceof SetCommunity) {
              SetCommunity sc = (SetCommunity) stmt;
              for (CommunityVar cv : collectCommunityVars(conf, sc.getExpr())) {
                _addCommunity.get(router).add( cv.getValue().replace("$","").replace("^","") );
              }
            } else if (stmt instanceof AddCommunity) {
              AddCommunity ac = (AddCommunity) stmt;
              for (CommunityVar cv : collectCommunityVars(conf, ac.getExpr())) {
                _addCommunity.get(router).add( cv.getValue().replace("$","").replace("^","") );
              }
            } else if (stmt instanceof DeleteCommunity) {
              DeleteCommunity dc = (DeleteCommunity) stmt;
              for (CommunityVar cv : collectCommunityVars(conf, dc.getExpr())) {
                _removeCommunity.get(router).add( cv.getValue().replace("$","").replace("^","") );
              }
            } else if ( stmt instanceof If ) {
                If i = (If) stmt;
                // do nothing
                //System.out.println("Check " + i.getGuard()+"\t"+i.getTrueStatements()+"\t"+i.getFalseStatements());
            }

        }

    }
/*
  public void check(Statement stmt, Configuration conf) {
      AstVisitor v = new AstVisitor();
      v.visit(
          conf,
          stmt,
          st -> {
            if ( st instanceof SetLocalPreference ) {
                SetLocalPreference i = (SetLocalPreference) st;
                if (i.getLocalPreference() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getLocalPreference();
                    System.out.println("1. \t" + li.getValue());
                }
            } else if ( st instanceof SetMetric ) {
                SetMetric i = (SetMetric) st;
                if (i.getMetric() instanceof LiteralLong) {
                    LiteralLong li = (LiteralLong) i.getMetric();
                    System.out.println("1. \t" + li.getValue());
                }
            }  else if ( st instanceof SetWeight ) {
                SetWeight i = (SetWeight) st;
                if (i.getWeight() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getWeight();
                    System.out.println("1. \t" + li.getValue());
                }
            } else if ( st instanceof SetAdministrativeCost ) {
                SetAdministrativeCost i = (SetAdministrativeCost) st;
                if (i.getAdmin() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getAdmin();
                    System.out.println("1. \t" + li.getValue());
                }
            }

          },
          expr -> {
            if (expr instanceof MatchCommunitySet) {
              MatchCommunitySet m = (MatchCommunitySet) expr;
              CommunitySetExpr ce = m.getExpr();
            }
          });
  }
*/
    public void appliesCommunity (String router, RoutingPolicy rp) {
        if (rp != null) {
            Configuration conf = g.getConfigurations().get(router);
            //System.out.println("CHECK RP " + rp.getStatements()+"\n*****************");
            for ( Statement st : rp.getStatements() ) {
                if ( st instanceof If ) {
                    If i = (If) st;
                    //System.out.println(i.getGuard()+"\t"+i.getTrueStatements()+"\t"+i.getFalseStatements());
                    if (i.getGuard() instanceof Conjunction) {
                        Conjunction cj = (Conjunction) i.getGuard();
                        for (BooleanExpr be : cj.getConjuncts()) {
                            if (be instanceof CallExpr) {
                                CallExpr ce = (CallExpr) be;
                                appliesRP(router, conf.getRoutingPolicies().get(ce.getCalledPolicyName()).getStatements());
                            }
                        }
                    } else if (i.getGuard() instanceof MatchPrefixSet) {
                        MatchPrefixSet m = (MatchPrefixSet) i.getGuard();
                        if (m.getPrefixSet() instanceof NamedPrefixSet) {
                            NamedPrefixSet x = (NamedPrefixSet) m.getPrefixSet();
                            RouteFilterList fl = conf.getRouteFilterLists().get(x.getName());
                            if (fl != null) {
                                for ( RouteFilterLine line : fl.getLines() ) {
                                    //System.out.println("IP. "+ line.getIpWildcard() +"\t"+i.getTrueStatements());
                                    if (line.getIpWildcard().intersects(dstIp)) {
                                        appliesRP(router, i.getTrueStatements());
                                    }        
                                }
                            }
                        }
                    } else if (i.getGuard() instanceof MatchCommunitySet) {
                        MatchCommunitySet m = (MatchCommunitySet) i.getGuard();
                        //System.out.println("@. " + m.getExpr() + "\t" +i.getTrueStatements());
                        // Right now allow it. afterwards need to modify, when it is set
                        CommunitySetExpr ce = m.getExpr();

                        //LineAction la = ce.getAction();
                        for (CommunityVar cv : collectCommunityVars(conf, ce)) {
                            String cvName = ""+cv.getValue().replace("$","").replace("^","");
                            if (isBlockedCommunity(router, cvName)) {
                                _actCommunity.get(router).add(cvName);
                                //System.out.println("Long - " + cvName);
                            }
                            setRouterCommunityCost(i.getTrueStatements(), router, cvName);
                        }
                        appliesRP(router, i.getTrueStatements());
                    }
                }
            }
            //System.out.println("############################");
        }

    }

    public boolean isBlockedCommunity(String router, String cvName) {
        Configuration conf = g.getConfigurations().get(router);
        _routerCommunityLp.put(router, new TreeMap<>());
        for (Entry<String, CommunityList> entry : conf.getCommunityLists().entrySet()) {
            String name = entry.getKey();
            CommunityList cl = entry.getValue();
            for (CommunityListLine cll : cl.getLines()) {
              CommunitySetExpr matchCondition = cll.getMatchCondition();
              LineAction la = cll.getAction();
              
              for (CommunityVar cv : collectCommunityVars(conf, matchCondition)) {
                if(la  == LineAction.DENY && cv.asLong()!=null) {
                  String thisCvName = "" + cv.getValue().replace("$","").replace("^","");
                  //System.out.println(thisCvName + "\tX\t" + cvName);
                  if (thisCvName.equals(cvName))   
                    return true;
                }
              }          
            }
        }
        return false;
    }

    public void setAllCommunity (String router, RoutingPolicy importRP, RoutingPolicy exportRP) {
        Configuration conf = g.getConfigurations().get(router);
        appliesCommunity(router, importRP);
        appliesCommunity(router, exportRP);
    }

    public void buildCommunity() {
        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            String router = entry.getKey();
            List<GraphEdge> edges = entry.getValue();
            Configuration conf = g.getConfigurations().get(router);

            for (Protocol proto : _protocols.get(router)) {
                for (GraphEdge e : edges) {
                    if (g.isEdgeUsed(conf, proto, e)) {
                        if (proto.isBgp()) {
                            RoutingPolicy importRP = g.findImportRoutingPolicy(router, proto, e);
                            RoutingPolicy exportRP = g.findExportRoutingPolicy(router, proto, e);
                            _addCommunity.put(router, new HashSet<>());
                            _removeCommunity.put(router, new HashSet<>());
                            _actCommunity.put(router, new HashSet<>());                            
                            setAllCommunity(router, importRP, exportRP);
                        }
                    }
                }
            }
        }

    }

    public boolean ibgpcontains(String srcname, String dstname) {
        boolean hasStatic = dg.isEdge(dg.getVertex(srcname+"-STATIC"), dg.getVertex(dstname+"-STATIC"));
        boolean adjacent = false;
        if (l2map!=null) {
            if (l2map.containsKey(srcname)) {
                adjacent = l2map.get(srcname).containsKey(dstname);
            }
        }
        return (hasStatic || adjacent);
    }

    /*
    public void connectEdge(String switchName, String routerName) {
        EdgeCost ec = new EdgeCost();
        for (Node routerProcesses : phyNodeMap.get(routerName)) {
            Node mainSwitch = multigraphNode.get(switchName);
            if (mainSwitch!=null) {
                dg.add(mainSwitch, routerProcesses, ec, protocol.SWITCH);
                dg.add(routerProcesses, mainSwitch, ec, protocol.SWITCH);
            }
            for (Node switchVLANs : _switchVLANMap.get(switchName).values()) {
                dg.add(switchVLANs, routerProcesses, ec, protocol.SWITCH);
                dg.add(routerProcesses, switchVLANs, ec, protocol.SWITCH);
            }
        }
    }*/

    public void connectRouterSwitch(String routerName, String switchName) {
        EdgeCost ec = new EdgeCost();
        for (Node routerProcesses : phyNodeMap.get(routerName)) {
            Node mainSwitch = multigraphNode.get(switchName);
            if (mainSwitch!=null) {
                dg.add(routerProcesses, mainSwitch, ec, protocol.SWITCH);
            }
            for (Node switchVLANs : _switchVLANMap.get(switchName).values()) {
                dg.add(routerProcesses, switchVLANs, ec, protocol.SWITCH);
            }
        }
    }

    public void connectSwitchRouter(String switchName, String routerName) {
        EdgeCost ec = new EdgeCost();
        for (Node routerProcesses : phyNodeMap.get(routerName)) {
            Node mainSwitch = multigraphNode.get(switchName);
            Set<String> allVlans = l2map.get(switchName).get(routerName);

            if ((allVlans.size() == 1 && allVlans.iterator().next().equals("*"))|| allVlans.size()==0) {
                if (mainSwitch!=null) {
                    dg.add(mainSwitch, routerProcesses, ec, protocol.SWITCH);
                }
            } else {
                for (String aVlan : allVlans) {
                    if (_switchVLANMap.get(switchName).containsKey(aVlan))
                        dg.add(_switchVLANMap.get(switchName).get(aVlan), routerProcesses, ec, protocol.SWITCH);                    
                }
            }
        }
    }

    public void connectSwitches(String srcSwitch, String dstSwitch) {
        EdgeCost ec = new EdgeCost();
        Set<String> allVlans = l2map.get(srcSwitch).get(dstSwitch);
        Node mainSrc = multigraphNode.get(srcSwitch);
        Node mainDst = multigraphNode.get(dstSwitch);

        if (allVlans.size() == 1 && allVlans.iterator().next().equals("*") || allVlans.size()==0) {
            if (mainSrc != null && mainDst != null)
                dg.add(mainSrc, mainDst, ec, protocol.SWITCH);

            for (String aVlan : allVlans) {
                if (mainSrc != null && _switchVLANMap.get(dstSwitch).containsKey(aVlan))
                    dg.add(mainSrc, _switchVLANMap.get(dstSwitch).get(aVlan), ec, protocol.SWITCH);
                if (mainDst != null && _switchVLANMap.get(srcSwitch).containsKey(aVlan))
                    dg.add(_switchVLANMap.get(srcSwitch).get(aVlan), mainDst, ec, protocol.SWITCH);
            }
            
        } else {
            for (String aVlan : allVlans) {
                if (_switchVLANMap.get(srcSwitch).containsKey(aVlan) && _switchVLANMap.get(dstSwitch).containsKey(aVlan))
                    dg.add(_switchVLANMap.get(srcSwitch).get(aVlan), _switchVLANMap.get(dstSwitch).get(aVlan), ec, protocol.SWITCH);
            }
        }

    }

    public void connectVRF(String srcRouter, String dstRouter, String srcname, String dstname, EdgeCost ec, boolean acl, protocol proto) {

        int srcVrfSize = _vrfMap.get(srcRouter).size();
        int dstVrfSize = _vrfMap.get(dstRouter).size();
        if (srcVrfSize == 0 && dstVrfSize == 0) {
            Node src = multigraphNode.get(srcname);
            Node dst = multigraphNode.get(dstname);
            dg.add(src, dst, ec, proto);
            dg.getEdge(src, dst).setIsACL(acl);

        } else if (srcVrfSize > 0 && dstVrfSize == 0) {
            Node dst = multigraphNode.get(dstname);
            for (String srcVRF : _vrfMap.get(srcRouter)) {
                Node src = multigraphNode.get((srcname+"-"+srcVRF));
                dg.add(src, dst, ec, proto);
                dg.getEdge(src, dst).setIsACL(acl);
            }

        } else if (srcVrfSize == 0 && dstVrfSize > 0) {
            Node src = multigraphNode.get(srcname);
            for (String dstVRF : _vrfMap.get(dstRouter)) {
                Node dst = multigraphNode.get((dstname+"-"+dstVRF));
                dg.add(src, dst, ec, proto);
                dg.getEdge(src, dst).setIsACL(acl);
            }

        } else if (srcVrfSize > 0 && dstVrfSize > 0) {
            for (String vrf : _vrfMap.get(srcRouter)) {
                Node src = multigraphNode.get((srcname+"-"+vrf));
                if (_vrfMap.get(dstRouter).contains(vrf)) {
                    Node dst = multigraphNode.get((dstname+"-"+vrf));
                    if (src == null || dst == null) {
                        System.out.println("There is an error in vrf-mapping while creating edges " + (srcname+"-"+vrf)
                         + "\t" + (dstname+"-"+vrf));
                    } else {
                        dg.add(src, dst, ec, proto);
                        dg.getEdge(src, dst).setIsACL(acl);
                    }
                }
            }
        }                 
    }

    public void connectOneSideVRF(String router, String srcname, String dstname, EdgeCost ec, boolean acl, protocol proto) {

        int vrfSize = _vrfMap.get(router).size();
        if (vrfSize == 0) {
            Node src = multigraphNode.get(srcname);
            Node dst = multigraphNode.get(dstname);
            dg.add(src, dst, ec, proto);
            dg.getEdge(src, dst).setIsACL(acl);

        } else if (vrfSize > 0) {
            Node dst = multigraphNode.get(dstname);
            for (String srcVRF : _vrfMap.get(router)) {
                Node src = multigraphNode.get((srcname+"-"+srcVRF));
                dg.add(src, dst, ec, proto);
                dg.getEdge(src, dst).setIsACL(acl);
            }
        }                 
    }


    public boolean hasSameVrf(String router, Node srcprot, Node dstprot) {
        for (String vrf : _vrfMap.get(router)) {
            if (srcprot.getId().contains(vrf) && dstprot.getId().contains(vrf))
                return true;
        }
        return false;
    }

    public void buildEdges() {

    	Node src, dst;
    	String pro, srcnode, dstnode, srcname, dstname;

        if (l2map != null) {
            for (String device : l2map.keySet()) {
                Configuration conf1 = g.getConfigurations().get(device);
                for ( String otherDevice : l2map.get(device).keySet()) {
                    Configuration conf2 = g.getConfigurations().get(otherDevice);

                    if (conf1.getDeviceType()==DeviceType.SWITCH && conf2.getDeviceType()==DeviceType.ROUTER)
                        connectSwitchRouter(device, otherDevice);
                    else if (conf1.getDeviceType()==DeviceType.ROUTER && conf2.getDeviceType()==DeviceType.SWITCH)
                        connectRouterSwitch(device, otherDevice);
                    else if (conf1.getDeviceType()==DeviceType.SWITCH && conf2.getDeviceType()==DeviceType.SWITCH)
                        connectSwitches(device, otherDevice);
                }
            }
        }

		// create edges connecting routing processes
    	for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
    		String router = entry.getKey();
    		List<GraphEdge> edges = entry.getValue();
    		Configuration conf = g.getConfigurations().get(router);

    		//System.out.println("VRF " + conf.getVrfs());
    		for (Protocol proto : _protocols.get(router)) {
    			for (GraphEdge e : edges) {

    				if (g.isEdgeUsed(conf, proto, e)) {
						srcnode = e.getRouter();
						dstnode = e.getPeer();
                        srcname = e.getRouter();
                        dstname = e.getPeer();

						if (srcnode == null || dstnode == null)
							continue;
    					if (proto.isBgp()) {
                            if (g.getIbgpNeighbors().containsKey(e)) {
                                //System.out.println("IBGP " + e.getRouter() + "\t" + e.getPeer());
                                continue;
                            }
                            RoutingPolicy importRP = g.findImportRoutingPolicy(router, proto, e);
                            RoutingPolicy exportRP = g.findExportRoutingPolicy(router, proto, e);
                            /*
                            if (importRP != null)
                                System.out.println(router + "\tImport\t" + importRP.getStatements());
                            if (exportRP != null)
                                System.out.println(router + "\tExport\t" + exportRP.getStatements());*/
                            if(blockFilter(importRP, conf) || blockFilter(exportRP, conf)) {
                                //System.out.print("Filter blocks it");
                                continue;
                            }
                            EdgeCost ec = new EdgeCost();
                            srcnode = srcnode + "-BGP";
                            dstnode = dstnode + "-BGP";
                            ec.setAS(1);
              				src = multigraphNode.get(srcnode);
    						dst = multigraphNode.get(dstnode);
					        //ec.setAD(10);
                            setBGPCost(importRP, exportRP, ec, conf);
                            //System.out.println("Cost " + ec);
                            boolean acl = blockAcl(e.getEnd(), e.getStart());

                            connectVRF(dstname, srcname, dstnode, srcnode, ec, acl, protocol.BGP);

                            //dg.add(src, dst, ec, protocol.BGP);
                            //dg.getEdge(dst, src).setIsACL(acl);                                

    					} else if (proto.isOspf()) {
    						srcnode = srcnode + "-OSPF";
    						dstnode = dstnode + "-OSPF";
							src = multigraphNode.get(srcnode);
    						dst = multigraphNode.get(dstnode);
    						EdgeCost ec = new EdgeCost();
					        
                            ec.setOSPF(e.getEnd().getOspfCost());
					        //ec.setAD(5);
							//dg.add(src, dst, ec, protocol.OSPF);
                            boolean acl = blockAcl(e.getStart(), e.getEnd());
                            //dg.getEdge(src, dst).setIsACL(acl);
    						connectVRF(srcname, dstname, srcnode, dstnode, ec, acl, protocol.OSPF);

    					} else if (proto.isStatic()) {

    						//skipping static routes to unreachable IP
    						if (dstnode == null)
    							continue;    						
							Set<Node> srcRouters = phyNodeMap.get(srcnode);
							Set<Node> dstRouters = phyNodeMap.get(dstnode);

    						srcnode = srcnode + "-STATIC";
    						dstnode = dstnode + "-STATIC";
							src = multigraphNode.get(srcnode);
    						dst = multigraphNode.get(dstnode);
    						EdgeCost ec = new EdgeCost();
					        //ec.setAD(50);
                            boolean applies = false;
                            for (StaticRoute sr : g.getStaticRoutes().get(conf.getHostname(), e.getStart().getName())) {
                                if (dstIp.containsIp(sr.getNextHopIp())) {
                                    applies = true;
                                    int staticWeight = sr.getMetric().intValue();
                                    ec.setWeight(staticWeight);
                                    break;
                                }
                            }
                            if (applies == false)
                                continue;

							dg.add(src, dst, ec, protocol.STAT);
                            boolean acl = blockAcl(e.getStart(), e.getEnd());
                            dg.getEdge(src, dst).setIsACL(acl);
                            
                            for (Node d : dstRouters) {
                                if (d.getType() == protocol.STAT)
                                    continue;
                                dg.add(src, d, ec, protocol.STAT);
                                dg.getEdge(src, d).setIsACL(acl);
                            }                            

							/*for (Node s : srcRouters) {
								if (s.getType() != protocol.STAT)
									continue;
								for (Node d : dstRouters) {
									if (d.getType() == protocol.STAT)
										continue;
									dg.add(s, d, ec, protocol.STAT);									
                                    dg.getEdge(s, d).setIsACL(acl);
								}
							}*/
    					}
    				}
    			}
    		}

    	}

        // add IBGP
        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            String router = entry.getKey();
            List<GraphEdge> edges = entry.getValue();
            Configuration conf = g.getConfigurations().get(router);
            for (Protocol proto : _protocols.get(router)) {
                for (GraphEdge e : edges) {
                    if (proto.isBgp() && g.isEdgeUsed(conf, proto, e) && g.getIbgpNeighbors().containsKey(e)) {
                        srcnode = e.getRouter();
                        dstnode = e.getPeer();
                        srcname = e.getRouter();
                        dstname = e.getPeer();

                        if (srcnode == null || dstnode == null)
                            continue;
                        if (ibgpcontains(srcnode, dstnode)) {
                            RoutingPolicy importRP = g.findImportRoutingPolicy(router, proto, e);
                            RoutingPolicy exportRP = g.findExportRoutingPolicy(router, proto, e);
                            if(blockFilter(importRP, conf) || blockFilter(exportRP, conf)) {
                                continue;
                            }
                            EdgeCost ec = new EdgeCost();
                            srcnode = srcnode + "-BGP";
                            dstnode = dstnode + "-BGP";
                            ec.setAS(0);
                            src = multigraphNode.get(srcnode);
                            dst = multigraphNode.get(dstnode);
                            setBGPCost(importRP, exportRP, ec, conf);
                            boolean acl = blockAcl(e.getEnd(), e.getStart());
                            //dg.add(dst, src, ec, protocol.IBGP);
                            //dg.getEdge(dst, src).setIsACL(acl);
                            connectVRF(srcname, dstname, srcnode, dstnode, ec, acl, protocol.IBGP);
                        }
                    }
                }
            }
        }

        // add edge between ibgp and ospf
        for (String node : phyNodeMap.keySet()) {
            if (!_hasIBGP.contains(node)) {
                continue;
            }
            Set<Node> allnode = phyNodeMap.get(node);
            for (Node srcprot : allnode) {
                if (srcprot.getType() == protocol.BGP) {
                    for (Node dstprot : allnode) {
                        if (dstprot.getType() == protocol.OSPF) {
                            EdgeCost ec = new EdgeCost();
                            //System.out.println("Mayadd "+ srcprot +"\t"+ dstprot);
                            if (hasSameVrf(node, srcprot, dstprot))
                                dg.add(srcprot, dstprot, ec, protocol.IBGP);
                            //connectVRF(node, node, srcprot.getId(), dstprot.getId(), ec, false, protocol.IBGP);
                        }
                    }
                }
            }
        }

    	// add edges representing redistribution
    	for (Entry<String, Configuration> entry : g.getConfigurations().entrySet()) {
    		String router = entry.getKey();
    		Configuration conf = entry.getValue();
    		for (Protocol proto : _protocols.get(router)) {
                String protocolName = "";
                //boolean hasSrcIBGP = false;
                protocol srcType = protocol.NONE;
                if (proto.isBgp()) {
                    protocolName = "BGP";
                    srcType = protocol.BGP;
                    /*if (multigraphNode.containsKey(router+"-IBGP")) {
                        hasSrcIBGP = true;
                    }*/
                }
                else if (proto.isOspf()) {
                    protocolName = "OSPF";
                    srcType = protocol.OSPF;
                }
                else if (proto.isStatic()) {
                    protocolName = "STATIC";
                    srcType = protocol.STAT;
                }
                else {
                    System.out.println(proto);
                }
    			srcnode = router + "-" + protocolName;
    			src = multigraphNode.get(srcnode);
    			RoutingPolicy pol = Graph.findCommonRoutingPolicy(conf, proto);
    			if (pol == null) {
    				continue;
    			}
    			Set<Protocol> ps = g.findRedistributedProtocols(conf, pol, proto);

    			for (Protocol p : ps) {
                    //boolean hasDstIBGP = false;
                    String protocolName2 = "";
                    protocol dstType = protocol.NONE;
                    if (p.isBgp()) {
                        protocolName2 = "BGP";
                        dstType = protocol.BGP;
                        /*if (multigraphNode.containsKey(router+"-IBGP")) {
                            hasDstIBGP = true;
                        }*/
                    }
                    else if (p.isOspf()) {
                        protocolName2 = "OSPF";
                        dstType = protocol.OSPF;
                    }
                    else if (p.isStatic()) {
                        protocolName2 = "STATIC";
                        dstType = protocol.STAT;
                    }
                    else {
                        System.out.println(p);
                    }

	    			dstnode = router + "-" + protocolName2;

	    			dst = multigraphNode.get(dstnode);
	    			//fix cost
                    if (src == null || dst == null) {
                        //if (hasSrcIBGP == hasDstIBGP) {
                            System.out.println("Can't add redis between "+ srcnode + " " + dstnode);
                            continue;
                        //}
                    }

					EdgeCost ec = new EdgeCost();
					if (srcType == protocol.BGP && dstType == protocol.OSPF) {
                        ec.setAS(1);
						//dg.add(dst, src, ec, protocol.REDISOB);
                        connectVRF(router, router, dstnode, srcnode, ec, false, protocol.REDISOB);
					} else if (srcType == protocol.OSPF && dstType == protocol.BGP) {
                        ec.setOSPF(1);
						//dg.add(dst, src, ec, protocol.REDISBO);
                        connectVRF(router, router, dstnode, srcnode, ec, false, protocol.REDISBO);
					} else if (srcType == protocol.BGP && dstType == protocol.STAT) {
                        ec.setOSPF(20);
						//dg.add(src, dst, ec, protocol.REDISSO);
                        connectOneSideVRF(router, srcnode, dstnode, ec, false, protocol.REDISSO);
					} else if (srcType == protocol.BGP && dstType == protocol.STAT) {
                        ec.setAS(1);
                        //dg.add(dst, src, ec, protocol.REDISSB);
                        connectOneSideVRF(router, srcnode, dstnode, ec, false, protocol.REDISSB);
                    } 
    			}
    		}
    	}
    }

    public void setAllCommunities(Node n, String name) {
        //System.out.println(name+"\t"+n);
        if(_actCommunity.containsKey(name))
            n.setBlockCommunity(_actCommunity.get(name));
        if(_addCommunity.containsKey(name))
            n.setAddCommunity(_addCommunity.get(name));
        if(_removeCommunity.containsKey(name))
            n.setRemoveCommunity(_removeCommunity.get(name));
    }

    public void buildNodes() {

    	Node src = null, dst = null;
    	String pro, srcnode, dstnode, srcname, dstname;

    	// create physical router to logical router-process mapping
        //System.out.println("Original Routers");
    	for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
    		String router = entry.getKey();
    		phyNodeMap.put(router, new HashSet<Node>());
    	}

        if (l2map != null) {
            for (String device : l2map.keySet()) {
                _switchVLANMap.put(device, new TreeMap<>());
                Configuration conf = g.getConfigurations().get(device);

                if (conf.getDeviceType() == DeviceType.SWITCH) {
                    Node switchNode = new Node(device, protocol.SWITCH);
                    dg.add(switchNode);
                    multigraphNode.put(device, switchNode);

                    for ( String otherDevice : l2map.get(device).keySet()) {
                        for (String vlan : l2map.get(device).get(otherDevice)) {
                            if (vlan!=null && !vlan.equals("*")) {
                                String deviceVLAN = device+"-vlan"+vlan;
                                switchNode = new Node(deviceVLAN, protocol.SWITCH);
                                dg.add(switchNode);
                                multigraphNode.put(deviceVLAN, switchNode);
                                _switchVLANMap.get(device).put(vlan, switchNode);
                            }
                        }
                    }
                }
            }
        }


    	// create nodes representing routing processes
    	for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
    		String router = entry.getKey();
    		List<GraphEdge> edges = entry.getValue();

            Configuration conf = g.getConfigurations().get(router);

            if (conf.getDeviceType() == DeviceType.SWITCH) {
                continue;
            }

    		for (Protocol proto : _protocols.get(router)) {
    			for (GraphEdge e : edges) {
    				//Configuration conf = g.getConfigurations().get(router);
    				srcname = e.getRouter();
    				dstname = e.getPeer();
    				if (g.isEdgeUsed(conf, proto, e)) {
						srcnode = e.getRouter();
						dstnode = e.getPeer();
                        if (proto.isConnected())
                            System.out.println("Connected: " + srcname + "\t" + dstnode);
						if (srcnode == null || dstnode == null)
							continue;
                        else if (proto.isBgp()) {
                            protocol thisProtocol = protocol.BGP;
    						if (g.getIbgpNeighbors().containsKey(e)) {
                                //srcnode = srcnode + "-IBGP";
                                //dstnode = dstnode + "-IBGP";
                                _hasIBGP.add(e.getRouter());
                                _hasIBGP.add(e.getPeer());
                                //thisProtocol = protocol.IBGP;
    						}
                            /* else {
                                srcnode = srcnode + "-BGP";
                                dstnode = dstnode + "-BGP";
                            }*/
                            srcnode = srcnode + "-BGP";
                            dstnode = dstnode + "-BGP";

    						if (!multigraphNode.containsKey(srcnode)) {
    							src = new Node(srcnode, thisProtocol);
    							dg.add(src);
    							multigraphNode.put(srcnode, src);
                                setAllCommunities(src, srcname);
                                phyNodeMap.get(e.getRouter()).add(src);

                                for (String vrfName : _vrfMap.get(srcname)) {
                                    String vrfNodeName = srcnode + "-" + vrfName;
                                    Node vrfNode = new Node(vrfNodeName, thisProtocol);
                                    dg.add(vrfNode);
                                    multigraphNode.put(vrfNodeName, vrfNode);
                                    setAllCommunities(vrfNode, srcname);

                                    if (e.getRouter()!=null && e.getRouter()!="") {
                                        phyNodeMap.get(e.getRouter()).add(vrfNode);
                                    }

                                }

    						}
    						if (!multigraphNode.containsKey(dstnode)) {
    							dst = new Node(dstnode, thisProtocol);
    							dg.add(dst);
    							multigraphNode.put(dstnode, dst);
                                setAllCommunities(dst, dstname);
                                phyNodeMap.get(e.getPeer()).add(dst);

                                for (String vrfName : _vrfMap.get(dstname)) {
                                    String vrfNodeName = dstnode + "-" + vrfName;
                                    Node vrfNode = new Node(vrfNodeName, thisProtocol);
                                    dg.add(vrfNode);
                                    multigraphNode.put(vrfNodeName, vrfNode);
                                    setAllCommunities(vrfNode, dstname);

                                    if (e.getPeer()!=null && e.getPeer()!="") {
                                        phyNodeMap.get(e.getPeer()).add(vrfNode);
                                    }
                                }

    						}/*
                            if (e.getRouter()!=null && e.getRouter()!="") {
                                phyNodeMap.get(e.getRouter()).add(src);
                            }
							if (e.getPeer()!=null && e.getPeer()!="") {
                                phyNodeMap.get(e.getPeer()).add(dst);
                            }*/
    					} else if (proto.isOspf()) {
    						srcnode = srcnode + "-OSPF";
    						dstnode = dstnode + "-OSPF";
							
    						if (!multigraphNode.containsKey(srcnode)) {
    							src = new Node(srcnode, protocol.OSPF);
    							dg.add(src);
    							multigraphNode.put(srcnode, src);
                                setAllCommunities(src, srcname);

                                for (String vrfName : _vrfMap.get(srcname)) {
                                    String vrfNodeName = srcnode + "-" + vrfName;
                                    Node vrfNode = new Node(vrfNodeName, protocol.OSPF);
                                    dg.add(vrfNode);
                                    multigraphNode.put(vrfNodeName, vrfNode);
                                    setAllCommunities(vrfNode, srcname);
                                    phyNodeMap.get(e.getRouter()).add(src);

                                    if (e.getRouter()!=null && e.getRouter()!="") {
                                        phyNodeMap.get(e.getRouter()).add(vrfNode);
                                    }

                                }
    						}
    						if (!multigraphNode.containsKey(dstnode)) {
    							dst = new Node(dstnode, protocol.OSPF);
    							dg.add(dst);
    							multigraphNode.put(dstnode, dst);
                                setAllCommunities(dst, dstname);
                                phyNodeMap.get(e.getPeer()).add(dst);

                                for (String vrfName : _vrfMap.get(dstname)) {
                                    String vrfNodeName = dstnode + "-" + vrfName;
                                    Node vrfNode = new Node(vrfNodeName, protocol.OSPF);
                                    dg.add(vrfNode);
                                    multigraphNode.put(vrfNodeName, vrfNode);
                                    setAllCommunities(vrfNode, dstname);
                                    
                                    if (e.getPeer()!=null && e.getPeer()!="") {
                                        phyNodeMap.get(e.getPeer()).add(vrfNode);
                                    }
                                }
    						}
                            /*
                            if (e.getRouter()!=null && e.getRouter()!="")
                                phyNodeMap.get(e.getRouter()).add(src);
                            if (e.getPeer()!=null && e.getPeer()!="")
                                phyNodeMap.get(e.getPeer()).add(dst);
                            */

    					} else if (proto.isStatic()) {
    						//skipping static routes to unreachable IP
    						srcnode = srcnode + "-STATIC";							
    						if (!multigraphNode.containsKey(srcnode)) {
    							src = new Node(srcnode, protocol.STAT);
    							dg.add(src);
    							multigraphNode.put(srcnode, src);
                                setAllCommunities(src, srcname);
    						}
                            if (e.getRouter()!=null && e.getRouter()!="")
                                phyNodeMap.get(e.getRouter()).add(src);
    						if (dstnode == null)
    							continue;
    						dstnode = dstnode + "-STATIC";
    						if (!multigraphNode.containsKey(dstnode)) {
    							dst = new Node(dstnode, protocol.STAT);
    							dg.add(dst);
    							multigraphNode.put(dstnode, dst);
                                setAllCommunities(dst, dstname);
    						}
                            if (e.getPeer()!=null && e.getPeer()!="")
                                phyNodeMap.get(e.getPeer()).add(dst);

    					} else {
                            continue;
                        }
    				}
    			}
    		}

    	}

    }


    public void buildRouterProtocol() {
    	// create physical router to protocols mapping
    	_protocols = new HashMap<>();
    	g.getConfigurations().forEach((router, conf) -> _protocols.put(router, new ArrayList<>()));
    	g.getConfigurations()
    	.forEach(
    		(router, conf) -> {
              List<Protocol> protos = _protocols.get(router);
              if (conf.getDefaultVrf().getOspfProcess() != null) {
                protos.add(Protocol.OSPF);
              }
              if (conf.getDefaultVrf().getBgpProcess() != null) {
                protos.add(Protocol.BGP);
              }
              if (!conf.getDefaultVrf().getStaticRoutes().isEmpty()) {
                protos.add(Protocol.STATIC);
              }
        });
    }
}
