package com.raffaeleconforti.noisefiltering.event;

import com.raffaeleconforti.automaton.Automaton;
import com.raffaeleconforti.automaton.Edge;
import com.raffaeleconforti.automaton.Node;
import com.raffaeleconforti.ilpsolverwrapper.impl.gurobi.Gurobi_Solver;
import com.raffaeleconforti.log.util.LogImporter;
import com.raffaeleconforti.noisefiltering.event.optimization.wrapper.WrapperInfrequentBehaviourSolver;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.model.XLog;

import java.util.Set;

/**
 * Created by Raffaele Conforti (conforti.raffaele@gmail.com) on 20/4/17.
 */
public class Test {

    public static void main(String[] args) throws Exception {
//        XLog log = LogImporter.importFromFile(new XFactoryNaiveImpl(), "/Volumes/Data/SharedFolder/Logs/ArtificialLess.xes.gz");
//
//        test(log, true);
//        test(log, false);

        Node<String> a = new Node<>("A");
        Node<String> b = new Node<>("B");
        Node<String> c = new Node<>("C");
        Node<String> d = new Node<>("D");

        Automaton<String> automaton = new Automaton<>();
        automaton.addNode(a, 4);
        automaton.addNode(b, 8);
        automaton.addNode(c, 2);
        automaton.addNode(d, 4);
        automaton.addEdge(a, b, 4);
        automaton.addEdge(b, b, 3);
        automaton.addEdge(b, c, 2);
        automaton.addEdge(b, d, 3);
        automaton.addEdge(c, b, 1);
        automaton.addEdge(c, d, 1);

        automaton.getAutomatonStart();
        automaton.getAutomatonEnd();
        automaton.createDirectedGraph();

        WrapperInfrequentBehaviourSolver wrapperInfrequentBehaviourSolver = new WrapperInfrequentBehaviourSolver(automaton, automaton.getEdges(), automaton.getNodes());
        Set<Edge<String>> infrequent = wrapperInfrequentBehaviourSolver.identifyRemovableEdges(new Gurobi_Solver());
        System.out.println(infrequent);
    }

    private static void test(XLog log, boolean useGurobi) {
        InfrequentBehaviourFilter filter = new InfrequentBehaviourFilter(new XEventNameClassifier(), useGurobi);
        filter.filterLog(log);
    }

}
