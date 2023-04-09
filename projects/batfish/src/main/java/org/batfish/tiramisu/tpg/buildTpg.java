package org.batfish.tiramisu.tpg;

import static org.batfish.symbolic.CommunityVarCollector.collectCommunityVars;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.batfish.datamodel.CommunityList;
import org.batfish.datamodel.CommunityListLine;
import org.batfish.datamodel.Configuration;
import org.batfish.datamodel.DeviceType;
import org.batfish.datamodel.Interface;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.IpWildcard;
import org.batfish.datamodel.IpWildcardIpSpace;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.Prefix;
import org.batfish.datamodel.RouteFilterLine;
import org.batfish.datamodel.RouteFilterList;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.routing_policy.RoutingPolicy;
import org.batfish.datamodel.routing_policy.expr.BooleanExpr;
import org.batfish.datamodel.routing_policy.expr.CallExpr;
import org.batfish.datamodel.routing_policy.expr.CommunitySetExpr;
import org.batfish.datamodel.routing_policy.expr.Conjunction;
import org.batfish.datamodel.routing_policy.expr.LiteralInt;
import org.batfish.datamodel.routing_policy.expr.LiteralLong;
import org.batfish.datamodel.routing_policy.expr.MatchCommunitySet;
import org.batfish.datamodel.routing_policy.expr.MatchPrefixSet;
import org.batfish.datamodel.routing_policy.expr.NamedPrefixSet;
import org.batfish.datamodel.routing_policy.statement.AddCommunity;
import org.batfish.datamodel.routing_policy.statement.DeleteCommunity;
import org.batfish.datamodel.routing_policy.statement.If;
import org.batfish.datamodel.routing_policy.statement.SetAdministrativeCost;
import org.batfish.datamodel.routing_policy.statement.SetCommunity;
import org.batfish.datamodel.routing_policy.statement.SetLocalPreference;
import org.batfish.datamodel.routing_policy.statement.SetMetric;
import org.batfish.datamodel.routing_policy.statement.SetWeight;
import org.batfish.datamodel.routing_policy.statement.Statement;
import org.batfish.multigraph.EdgeCost;
import org.batfish.symbolic.CommunityVar;
import org.batfish.symbolic.Graph;
import org.batfish.symbolic.GraphEdge;
import org.batfish.symbolic.Protocol;
import org.batfish.tiramisu.NodeType;
import org.batfish.tiramisu.rag.Rag;
import org.batfish.tiramisu.rag.buildRag;


public class buildTpg implements Runnable {

	Graph g;
	Tpg tpg;
    buildRag ragBuild;
    Rag rag;

	Map<String, List<Protocol>> _protocols;
    private Map<String, TpgNode> multigraphNode;
    private Map<String, Set<TpgNode>> phyNodeMap;
    private Map<String, Set<TpgEdge>> phyEdgeMap;

    public Map<String, Set<String>> _actCommunity;
    public Map<String, Set<String>> _addCommunity;
    public Map<String, Set<String>> _removeCommunity;
    public Map<String, Map<String, EdgeCost>> _routerCommunityLp;

    IpWildcard srcIp;
    IpWildcard dstIp;

    public TpgNode srcTC = null;
    public TpgNode dstTC = null;

    String srcNodeName = null;
    String dstNodeName = null;

    long generateTime = 0;

    int countmap = 0;

    ConcurrentHashMap<String, Tpg> conMap = null;

    boolean correctSrcDst = false;

    public buildTpg(Graph g, IpWildcard src, IpWildcard dst) {
        this.g = g;
        tpg = new Tpg();
        srcIp = src;
        dstIp = dst;
        multigraphNode = new HashMap<>();
        phyNodeMap = new HashMap<>();
        phyEdgeMap = new HashMap<>();
        ragBuild = new buildRag(g, srcIp, dstIp);
    }

    public buildTpg(Graph g, String src, String dst, IpWildcard srcip, IpWildcard dstip,
     ConcurrentHashMap<String, Tpg> conncMap) {
        this.g = g;
        tpg = new Tpg();
        srcNodeName = src;
        dstNodeName = dst;
        srcIp = srcip;
        dstIp = dstip;
        multigraphNode = new HashMap<>();
        phyNodeMap = new HashMap<>();
        phyEdgeMap = new HashMap<>();
        ragBuild = new buildRag(g, srcNodeName, dstNodeName, srcIp, dstIp);
        conMap = conncMap;
    }

    public Tpg getTpg() {
    	return tpg;
    }

    public void buildGraph() {
        //long startTime = System.nanoTime();
        ragBuild.run();
        rag = ragBuild.getRag();
        rag.taint();
        initialize();
        buildTpgNodes();
        buildTpgEdges();
        setSrcDst();
//        tpg.print();
//        new PolicyVerify(tpg).verify("WAYPOINT");
        //long endTime = System.nanoTime();
//        setNodes();
//        tpg.setPhysicalMap(phyEdgeMap);
        String key = srcIp.toString() + "-" + dstIp.toString();
//        if (conMap != null && correctSrcDst == true) {
//            conMap.put(key, tpg);
//        }
    }

    @Override
    public void run() {
        buildGraph();
    }

    public void draw() {
        System.out.println("Edges");
    }

    public void initialize() {

        // create physical router to logical router-process mapping
        //System.out.println("Original Routers");
        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            String router = entry.getKey();
            phyNodeMap.put(router, new HashSet<TpgNode>());
        }

    }

    public void buildTpgNodes() {
        Map<String, Configuration> confs = g.getConfigurations();
        confs.forEach(
                (router, conf) -> {
                    if (conf.getDeviceType() == DeviceType.SWITCH) {

                    }else{
                        if (conf.getDefaultVrf().getOspfProcess() != null) {
                            tpg.addNode(new TpgNode(router, Protocol.OSPF));
                        }
                        if (conf.getDefaultVrf().getBgpProcess() != null) {
                            tpg.addNode(new TpgNode(router, Protocol.BGP));
                        }
//                        if (!conf.getDefaultVrf().getStaticRoutes().isEmpty()) {
//                            protos.add(Protocol.STATIC);
//                        }
                    }
                });
        //System.out.println("Edges interface");
        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
            List<GraphEdge> edges = entry.getValue();
            for (GraphEdge e : edges) {
                if (e.getRouter()!=null && e.getPeer()!=null) {
//                    tpg.addNode(new TpgNode(e.getRouter(), Protocol.CONNECTED, NodeType.VLAN_IN,e.getPeer()));
//                    tpg.addNode(new TpgNode(e.getRouter(), Protocol.CONNECTED, NodeType.VLAN_OUT,e.getPeer()));
                    if(blockAcl(e.getStart(),true)){
                        tpg.addNode(new TpgNode(e.getRouter(), Protocol.CONNECTED, NodeType.VLAN_OUT,e.getPeer(),true));
                    }else{
                        tpg.addNode(new TpgNode(e.getRouter(), Protocol.CONNECTED, NodeType.VLAN_OUT,e.getPeer(),false));
                    }
                    if(blockAcl(e.getEnd(),false)){
                        tpg.addNode(new TpgNode(e.getPeer(), Protocol.CONNECTED, NodeType.VLAN_IN,e.getRouter(),true));
                    }else{
                        tpg.addNode(new TpgNode(e.getPeer(), Protocol.CONNECTED, NodeType.VLAN_IN,e.getRouter(),false));
                    }
                }
            }
        }

    }

    public void buildTpgEdges() {
        for (String router:g.getRouters()) {
            //Layers 1 & 2
            for (TpgNode node : tpg.selectNodes(null, null, NodeType.VLAN_IN, router)) {
                tpg.addEdge(tpg.selectNodes(router, Protocol.CONNECTED, NodeType.VLAN_OUT, node.getDeviceId()).get(0),
                    node);
            }
            //OSPF's dependence on L2
            List<TpgNode> OSPFNodes = tpg.selectNodes(router, Protocol.OSPF, null, null);
            if (OSPFNodes.size() > 0) {
                for (TpgNode node : tpg.selectNodes(router,
                    Protocol.CONNECTED,
                    NodeType.VLAN_OUT,
                    null)) {
                    if(tpg.selectNodes(node.getVlanPeerId(), Protocol.OSPF, null, null).size()>0){
                        tpg.addEdge(OSPFNodes.get(0), node);
                    }
                }
            }
            //Routes to the destination
            for(Protocol p:rag.getNodeMap().get(router).keySet()){
                if(p.equals(Protocol.BGP) || p.equals(Protocol.OSPF)){
                    if(rag.getNodeMap().get(router).get(p).isTaint()){
                        for(TpgNode node : tpg.selectNodes(router, Protocol.CONNECTED, NodeType.VLAN_IN, null)){
                            tpg.addEdge(node,
                                tpg.selectNodes(router, p, NodeType.NONE, null).get(0));
                        }
                    }
                }
            }
            if(rag.getNodeMap().get(router).get(Protocol.BGP).isTaint()){

            }
        }
            //BGP's dependence on connected and OSPF routes
            //ebgp
            for(GraphEdge e:g.getEbgpNeighbors().keySet()){
                tpg.addEdge(tpg.selectNodes(e.getRouter(), Protocol.BGP, NodeType.NONE, null).get(0),
                    tpg.selectNodes(e.getRouter(), Protocol.CONNECTED, NodeType.VLAN_OUT, e.getPeer()).get(0));
            }
            //ibgp
            for(GraphEdge e:g.getIbgpNeighbors().keySet()){
                tpg.addEdge(tpg.selectNodes(e.getRouter(), Protocol.BGP, NodeType.NONE, null).get(0),
                    tpg.selectNodes(e.getRouter(), Protocol.OSPF, NodeType.NONE,null).get(0));
            }
    }

    public void setSrcDst(){
        if (srcNodeName == null || srcNodeName.equals("") || dstNodeName == null || dstNodeName.equals(
            "")) {
            for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
                String router = entry.getKey();
                Configuration conf = g.getConfigurations().get(router);
                Set<Prefix> prefixes = Graph.getOriginatedNetworks(conf, Protocol.CONNECTED);
                for (Prefix pp : prefixes) {
                    if (pp.containsPrefix(srcIp.toPrefix())) {
                        srcNodeName = router;
                    }
                    if (pp.containsPrefix(dstIp.toPrefix())) {
                        dstNodeName = router;
                    }
                }
            }
        }

        if (srcNodeName != null) {
            TpgNode srcNode = new TpgNode(srcNodeName, Protocol.CONNECTED,NodeType.SRC,null);
            tpg.addNode(srcNode);
            for(TpgNode n:tpg.selectNodes(srcNodeName,null,null,null)){
                if(n.getProtocol() == Protocol.BGP || n.getProtocol() == Protocol.OSPF){
                    if(rag.getNodeMap().get(srcNodeName).get(n.getProtocol()).isTaint()){
                        tpg.addEdge(srcNode,n);
                    }
                }
            }
            tpg.setSrcNode(srcNode);
        }
        if (dstNodeName != null) {
            TpgNode dstNode = new TpgNode(dstNodeName, Protocol.CONNECTED,NodeType.DST,null);
            tpg.addNode(dstNode);
            for(TpgNode n:tpg.selectNodes(dstNodeName,Protocol.CONNECTED,NodeType.VLAN_IN,null)){
                tpg.addEdge(n,dstNode);
            }
            tpg.setDstNode(dstNode);
        }
    }

    public boolean blockAcl(Interface inter, boolean out) {
        //Interface i = ge.getStart();
        IpAccessList bound;
        if(out){
            bound = inter.getOutgoingFilter();
        }else{
            bound = inter.getIncomingFilter();
        }

        if (bound != null) {
            for ( IpAccessListLine line : bound.getLines() ) {
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

    public void buildCommunity(){
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
                            appliesCommunity(router, importRP);
                            appliesCommunity(router, exportRP);
                        }
                    }
                }
            }
        }
    }

    public void appliesCommunity (String router, RoutingPolicy rp) {
        if (rp != null) {
            //            appliesRP(router,rp.getStatements());
            Configuration conf = g.getConfigurations().get(router);
            //System.out.println("CHECK RP " + rp.getStatements()+"\n*****************");
            for ( Statement st : rp.getStatements() ) {
                if ( st instanceof If) {
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

    public void setRouterCommunityCost(List<Statement> stmt, String router, String comm) {
        EdgeCost ec = new EdgeCost();
        boolean set = false;
        for (Statement st : stmt) {
            if ( st instanceof SetLocalPreference) {
                SetLocalPreference i = (SetLocalPreference) st;
                if (i.getLocalPreference() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getLocalPreference();
                    ec.setLP(li.getValue());
                    set = true;
                }
            } else if ( st instanceof SetMetric) {
                SetMetric i = (SetMetric) st;
                if (i.getMetric() instanceof LiteralLong) {
                    LiteralLong li = (LiteralLong) i.getMetric();
                    ec.setMED(((int)li.getValue()));
                    set = true;
                }
            }  else if ( st instanceof SetWeight) {
                SetWeight i = (SetWeight) st;
                if (i.getWeight() instanceof LiteralInt) {
                    LiteralInt li = (LiteralInt) i.getWeight();
                    ec.setWeight(li.getValue());
                    set = true;
                }
            } else if ( st instanceof SetAdministrativeCost) {
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
    //    public void addDependenceGraph(GraphEdge depEdge) {
//
//        String prefix = intfName(depEdge.getRouter(), depEdge.getStart().getName());
//
//        String protName = null, protName1 = null, protName2 = null;
//        TpgNode protNode = null;
//        //System.out.println("Prefix\t" + prefix + "\t" + depEdge.getStart().getName() + "\t" + depEdge.getEnd().getName());
//        //creating all relevant nodes
//        //System.out.println("#################NODES        CREATED########################");
//        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
//            String router = entry.getKey();
//            List<GraphEdge> edges = entry.getValue();
//            Configuration conf = g.getConfigurations().get(router);
//
//            if (_protocols.get(router).contains(Protocol.OSPF)) {
//                protName = prefix + '_' + createName(router, "OSPF");
//                protNode = new TpgNode(protName, protocol.OSPF, router);
//                //System.out.println(protName);
//                tpg.add(protNode);
//                multigraphNode.put(protName, protNode);
//                phyNodeMap.get(router).add(protNode);
//            }
//            //System.out.println("VRF " + conf.getVrfs());
//            for (GraphEdge e : edges) {
//                if (g.isEdgeUsed(conf, Protocol.BGP, e)) {
//                    continue;
//                }
//                if (e.getStart()!=null && e.getEnd()!=null) {
//                    protName = prefix + '_' + intfName(e.getRouter(), e.getStart().getName());
//                    addInterface(e.getRouter(), protName);
//                    //System.out.println(protName+"\tIO");
//                    //protName = prefix + '_' + intfName(e.getPeer(), e.getEnd().getName());
//                    //addInterface(e.getPeer(), protName);
//                }
//            }
//        }
//        //System.out.println("#########################################");
//        // create all relevant edges
//        for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
//            String router = entry.getKey();
//            List<GraphEdge> edges = entry.getValue();
//            Configuration conf = g.getConfigurations().get(router);
//
//            for (GraphEdge e : edges) {
//                if (e.getStart()!=null && e.getEnd()!=null) {
//                    if (g.isEdgeUsed(conf, Protocol.BGP, e)) {
//                        continue;
//                    }
//                    protName1 = prefix + '_' + intfName(e.getRouter(), e.getStart().getName());
//                    protName2 = prefix + '_' + intfName(e.getPeer(), e.getEnd().getName());
//                    addIntraIntfEdge(protName1);
//                    addIntraIntfEdge(protName2);
//                    boolean acl = blockAcl(e.getEnd(), e.getStart());
//                    addInterIntfEdge(protName1, protName2, e.getRouter(), e.getPeer(), acl);
//
//                    //exit edges
//                    //System.out.println("@@\t"+e.getPeer()+"\t"+depEdge.getPeer());
//                    if ( e.getPeer().equals(depEdge.getPeer())) {
//                        //System.out.println("##\t"+protName2);
//                        addTpgEdge(protName2+"_I", createName(depEdge.getPeer(), "BGP"), protocol.BGP);
//                    }
//
//
//                    if (g.isEdgeUsed(conf, Protocol.OSPF, e)) {
//                        EdgeCost ec = new EdgeCost();
//                        ec.setOSPF(e.getEnd().getOspfCost());
//                        addTpgEdge(prefix + '_' + createName(router, "OSPF"), protName1+"_O", protocol.OSPF, ec);
//                        continue;
//                    }
//                }
//            }
//        }
//        /*
//        System.out.println("Exit edge\t"+intfName(depEdge.getPeer(), depEdge.getEnd().getName()));
//        protName2 = prefix + '_' + intfName(depEdge.getPeer(), depEdge.getEnd().getName()) + "_I";
//        addTpgEdge(protName2, createName(depEdge.getPeer(), "BGP"), protocol.BGP);
//        */
//    }
//
//    public void addTpgEdge(String name1, String name2, protocol prot) {
//        TpgNode in = multigraphNode.get(name1);
//        TpgNode out = multigraphNode.get(name2);
//        if(in==null || out==null) {
//            //System.out.println("addTpgEdge Null val " + name1 + "\t" + name2 + "\t"  + in + "\t"  + out);
//            return;
//        }
//        tpg.add(in, out, new EdgeCost(), prot);
//    }
//
//    public void addTpgEdge(String name1, String name2, protocol prot, EdgeCost ec) {
//        TpgNode in = multigraphNode.get(name1);
//        TpgNode out = multigraphNode.get(name2);
//        if(in==null || out==null) {
//            //System.out.println("addTpgEdge Null val " + name1 + "\t" + name2 + "\t"  + in + "\t"  + out);
//            return;
//        }
//        tpg.add(in, out, ec, prot);
//    }
//
//    public void addIntraIntfEdge(String name) {
//        TpgNode in = multigraphNode.get(name+"_I");
//        TpgNode out = multigraphNode.get(name+"_O");
//        if(in==null || out==null) {
//            //System.out.println("addIntraIntfEdge Null val");
//            return;
//        }
//        tpg.add(in, out, new EdgeCost(), protocol.INTF);
//    }
//
//    public void addInterIntfEdge(String name1, String name2, String srcRouter, String dstRouter, boolean acl) {
//        TpgNode in = multigraphNode.get(name1+"_O");
//        TpgNode out = multigraphNode.get(name2+"_I");
//        if(in==null || out==null) {
//            //System.out.println("addInterIntfEdge Null val");
//            return;
//        }
//        String key = srcRouter + "_" + dstRouter;
//        TpgEdge thisEdge = tpg.add(in, out, new EdgeCost(), protocol.INTF);
//
//        if (acl) {
//            thisEdge.setACL();
//        }
//
//        thisEdge.setRemovable();
//
//        if (!phyEdgeMap.containsKey(key)) {
//            phyEdgeMap.put(key, new HashSet<>());
//        }
//        phyEdgeMap.get(key).add(thisEdge);
//    }
//
//
//    public void addInterface(String router, String protName) {
//        String inName = protName+"_I";
//
//        if (multigraphNode.containsKey(inName))
//            return;
//        TpgNode protNode = new TpgNode(inName, protocol.INTF, router);
//        tpg.add(protNode);
//        multigraphNode.put(inName, protNode);
//        phyNodeMap.get(router).add(protNode);
//
//        String outName = protName+"_O";
//        protNode = new TpgNode(outName, protocol.INTF, router);
//        tpg.add(protNode);
//        multigraphNode.put(outName, protNode);
//        phyNodeMap.get(router).add(protNode);
//    }
//
//    public String createName(String router, String prefix) {
//        return router+"-"+prefix;
//    }
//
//    public String bgpName(String router) {
//        return router+"-BGP";
//    }
//
//    public String bgpName(String router, String next) {
//        return router+"-BGP_"+next;
//    }
//
//    public String intfName(String router, String intf) {
//        return router+"_"+intf;
//    }
//
//    public String ospfName(String router) {
//        return router+"-OSPF";
//    }
//
//    public void setNodes() {
//
//        //System.out.println(srcNodeName + "\t" + dstNodeName);
//
//        if (srcNodeName == null || srcNodeName == "" || dstNodeName == null || dstNodeName == "") {
//            for (Entry<String, List<GraphEdge>> entry : g.getEdgeMap().entrySet()) {
//                String router = entry.getKey();
//                Configuration conf = g.getConfigurations().get(router);
//                for (Protocol proto : _protocols.get(router)) {
//                    Set<Prefix> prefixes = Graph.getOriginatedNetworks(conf, proto);
//                    //System.out.println(router + "\t" + prefixes);
//                    for (Prefix pp : prefixes) {
//                        if (pp.containsPrefix(srcIp.toPrefix())) {
//                            srcNodeName = router;
//                        }
//                        if (pp.containsPrefix(dstIp.toPrefix())) {
//                            dstNodeName = router;
//                        }
//                    }
//                }
//            }
//        }
//
//        if (srcNodeName != null && phyNodeMap.containsKey(srcNodeName)) {
//            //System.out.println(srcNodeName);
//            srcTC = new TpgNode(srcNodeName, protocol.SRC, srcNodeName);
//            tpg.add(srcTC);
//            String ribSrc = createName(srcNodeName, "RIB");
//            TpgNode ribSrcNode = tpg.getVertex(ribSrc);
//            if ( ribSrcNode != null) {
//                tpg.add(srcTC, ribSrcNode, new EdgeCost(), protocol.SRC);
//            } else {
//                //System.out.println("Invalid Source");
//            }
//
//            /*
//            String ospfRouter = createName(srcNodeName, "OSPF");
//            String bgpRouter = createName(srcNodeName, "BGP");
//
//            TpgNode ospfNode = tpg.getVertex(ospfRouter);
//            if (ospfNode != null) {
//                tpg.add(ospfNode, srcTC, new EdgeCost(), protocol.SRC);
//            }
//            TpgNode bgpNode = tpg.getVertex(bgpRouter);
//            if (bgpNode != null) {
//                tpg.add(bgpNode, srcTC, new EdgeCost(), protocol.SRC);
//            }*/
//        }
//        if (dstNodeName != null && phyNodeMap.containsKey(dstNodeName)) {
//            //System.out.println(dstNodeName);
//            dstTC = new TpgNode(dstNodeName, protocol.DST, dstNodeName);
//            tpg.add(dstTC);
//            String ribDst = createName(dstNodeName, "RIB");
//            TpgNode ribDstNode = tpg.getVertex(ribDst);
//            if ( ribDstNode != null) {
//                tpg.add( ribDstNode, dstTC, new EdgeCost(), protocol.DST);
//            } else {
//                //System.out.println("Invalid Destination");
//            }
//            /*
//            String ospfRouter = createName(dstNodeName, "OSPF");
//            String bgpRouter = createName(dstNodeName, "BGP");
//
//            TpgNode ospfNode = tpg.getVertex(ospfRouter);
//            if (ospfNode != null) {
//                tpg.add(dstTC, ospfNode, new EdgeCost(), protocol.DST);
//            }
//            TpgNode bgpNode = tpg.getVertex(bgpRouter);
//            if (bgpNode != null) {
//                tpg.add(dstTC, bgpNode, new EdgeCost(), protocol.DST);
//            }
//            */
//        }
//        correctSrcDst = false;
//        if (srcTC!=null && dstTC!=null) {
//            tpg.setSourceDest(srcTC, dstTC);
//            correctSrcDst = true;
//        }
//    }
//
//    public boolean blockFilter(RoutingPolicy rp, Configuration conf) {
//        if (rp == null)
//            return false;
//        for ( Statement st : rp.getStatements() ) {
//            if ( st instanceof If ) {
//                If i = (If) st;
//                if (i.getGuard() instanceof MatchPrefixSet) {
//                    MatchPrefixSet m = (MatchPrefixSet) i.getGuard();
//                    if (m.getPrefixSet() instanceof NamedPrefixSet) {
//                        NamedPrefixSet x = (NamedPrefixSet) m.getPrefixSet();
//                        RouteFilterList fl = conf.getRouteFilterLists().get(x.getName());
//                        if (fl != null) {
//                            for ( RouteFilterLine line : fl.getLines() ) {
//                                if (line.getAction() == LineAction.DENY && line.getIpWildcard().intersects(dstIp))
//                                    return true;
//                            }
//
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    public boolean blockAcl(Interface out, Interface in) {
//        //Interface i = ge.getStart();
//
//        IpAccessList outbound = out.getOutgoingFilter();
//
//        if (outbound != null) {
//            for ( IpAccessListLine line : outbound.getLines() ) {
//                if (line.getAction() == LineAction.DENY) {
//                    if ( line.getMatchCondition() instanceof MatchHeaderSpace) {
//                        MatchHeaderSpace mhs = (MatchHeaderSpace)line.getMatchCondition();
//
//                        if (mhs.getHeaderspace().getSrcIps() instanceof IpWildcardIpSpace &&
//                            mhs.getHeaderspace().getDstIps() instanceof IpWildcardIpSpace) {
//                            IpWildcardIpSpace srcWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getSrcIps();
//                            IpWildcardIpSpace dstWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getDstIps();
//                            if (srcWildCard.getIpWildcard().intersects(srcIp) &&
//                                dstWildCard.getIpWildcard().intersects(dstIp)) {
//                                //System.out.println("Blocked by ACL " + mhs.getHeaderspace().getSrcIps() +
//                                //    "\t" + mhs.getHeaderspace().getDstIps());
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        //Interface i = ge.getEnd();
//        IpAccessList inbound = in.getIncomingFilter();
//
//        if (inbound != null) {
//            for ( IpAccessListLine line : inbound.getLines() ) {
//                if (line.getAction() == LineAction.DENY) {
//                    if ( line.getMatchCondition() instanceof MatchHeaderSpace) {
//                        MatchHeaderSpace mhs = (MatchHeaderSpace)line.getMatchCondition();
//
//                        if (mhs.getHeaderspace().getSrcIps() instanceof IpWildcardIpSpace &&
//                            mhs.getHeaderspace().getDstIps() instanceof IpWildcardIpSpace) {
//                            IpWildcardIpSpace srcWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getSrcIps();
//                            IpWildcardIpSpace dstWildCard = (IpWildcardIpSpace)mhs.getHeaderspace().getDstIps();
//                            if (srcWildCard.getIpWildcard().intersects(srcIp) &&
//                                dstWildCard.getIpWildcard().intersects(dstIp)) {
//                                //System.out.println("Blocked by ACL " + mhs.getHeaderspace().getSrcIps() +
//                                //    "\t" + mhs.getHeaderspace().getDstIps());
//                                return true;
//                            }
//                        }
//                    }
//                }
//            }
//        }
//        return false;
//    }
//
//    public void setBGPCost(RoutingPolicy importRP, RoutingPolicy exportRP, EdgeCost ec, Configuration conf) {
//        setBGPCostPol(importRP, ec, conf);
//        setBGPCostPol(exportRP, ec, conf);
//    }
//
//    public void setBGPCostPol(RoutingPolicy rp, EdgeCost ec, Configuration conf) {
//        if (rp != null) {
//            for (Statement st : rp.getStatements()) {
//                if ( st instanceof SetLocalPreference ) {
//                    SetLocalPreference i = (SetLocalPreference) st;
//                    if (i.getLocalPreference() instanceof LiteralInt) {
//                        LiteralInt li = (LiteralInt) i.getLocalPreference();
//                        ec.setLP(li.getValue());
//                    }
//                } else if ( st instanceof SetMetric ) {
//                    SetMetric i = (SetMetric) st;
//                    if (i.getMetric() instanceof LiteralLong) {
//                        LiteralLong li = (LiteralLong) i.getMetric();
//                        ec.setMED(((int)li.getValue()));
//                    }
//                }  else if ( st instanceof SetWeight ) {
//                    SetWeight i = (SetWeight) st;
//                    if (i.getWeight() instanceof LiteralInt) {
//                        LiteralInt li = (LiteralInt) i.getWeight();
//                        ec.setWeight(li.getValue());
//                    }
//                } else if ( st instanceof SetAdministrativeCost ) {
//                    SetAdministrativeCost i = (SetAdministrativeCost) st;
//                    if (i.getAdmin() instanceof LiteralInt) {
//                        LiteralInt li = (LiteralInt) i.getAdmin();
//                        ec.setAD(li.getValue());
//                    }
//                } else if ( st instanceof If ) {
//                    If i = (If) st;
//                    //System.out.println(i.getGuard()+"\t"+i.getTrueStatements()+"\t"+i.getFalseStatements());
//                    if (i.getGuard() instanceof MatchPrefixSet) {
//                        MatchPrefixSet m = (MatchPrefixSet) i.getGuard();
//                        if (m.getPrefixSet() instanceof NamedPrefixSet) {
//                            NamedPrefixSet x = (NamedPrefixSet) m.getPrefixSet();
//                            RouteFilterList fl = conf.getRouteFilterLists().get(x.getName());
//                            if (matches(fl)) {
//                                for ( Statement trueStmt : i.getTrueStatements()) {
//                                    if ( trueStmt instanceof SetLocalPreference ) {
//                                        SetLocalPreference lp = (SetLocalPreference) trueStmt;
//                                        if (lp.getLocalPreference() instanceof LiteralInt) {
//                                            LiteralInt li = (LiteralInt) lp.getLocalPreference();
//                                            ec.setLP(li.getValue());
//                                        }
//                                    } else if ( trueStmt instanceof SetMetric ) {
//                                        SetMetric lmetric = (SetMetric) trueStmt;
//                                        if (lmetric.getMetric() instanceof LiteralLong) {
//                                            LiteralLong li = (LiteralLong) lmetric.getMetric();
//                                            ec.setMED(((int)li.getValue()));
//                                        }
//                                    }  else if ( trueStmt instanceof SetWeight ) {
//                                        SetWeight lweight = (SetWeight) trueStmt;
//                                        if (lweight.getWeight() instanceof LiteralInt) {
//                                            LiteralInt li = (LiteralInt) lweight.getWeight();
//                                            ec.setWeight(li.getValue());
//                                        }
//                                    }  else if ( trueStmt instanceof SetAdministrativeCost ) {
//                                        SetAdministrativeCost lad = (SetAdministrativeCost) trueStmt;
//                                        if (lad.getAdmin() instanceof LiteralInt) {
//                                            LiteralInt li = (LiteralInt) lad.getAdmin();
//                                            ec.setAD(li.getValue());
//                                        }
//                                    }
//                                }
//                            }
//                        }
//
//                    }
//                }
//            }
//
//        }
//    }
//
//    public boolean matches(RouteFilterList fl) {
//        if (fl != null) {
//            for ( RouteFilterLine line : fl.getLines() ) {
//                if (line.getIpWildcard().intersects(dstIp))
//                    return true;
//            }
//        }
//        return false;
//    }
//
//    public void buildRouterProtocol() {
//    	// create physical router to protocols mapping
//    	_protocols = new HashMap<>();
//
//    }
}
