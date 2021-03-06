package com.raffaeleconforti.measurements.impl;

import au.edu.qut.petrinet.tools.SoundnessChecker;
import com.raffaeleconforti.measurements.Measure;
import com.raffaeleconforti.measurements.MeasurementAlgorithm;
import com.raffaeleconforti.wrapper.MiningAlgorithm;
import com.raffaeleconforti.wrapper.PetrinetWithMarking;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.processtree.ProcessTree;

import java.util.Random;

/**
 * Created by Raffaele Conforti (conforti.raffaele@gmail.com) on 18/10/2016.
 */
public class XFoldAlignmentBasedFitness implements MeasurementAlgorithm {

    private int fold = 3;
    private XFactory factory = new XFactoryNaiveImpl();
    private XLog log;
    private Random r = new Random(123456789);

    @Override
    public boolean isMultimetrics() { return false; }

    @Override
    public Measure computeMeasurement(UIPluginContext pluginContext, XEventClassifier xEventClassifier, ProcessTree processTree, MiningAlgorithm miningAlgorithm, XLog log) {
        return null;
    }

    @Override
    public Measure computeMeasurement(UIPluginContext pluginContext, XEventClassifier xEventClassifier, PetrinetWithMarking petrinetWithMarking, MiningAlgorithm miningAlgorithm, XLog log) {
        Measure measure = new Measure();
        double fitness = 0.0;
        this.log = log;
        XLog[] logs = createdXFolds();

        SoundnessChecker checker = new SoundnessChecker(petrinetWithMarking.getPetrinet());
        if( !checker.isSound() ) {
            measure.addMeasure(getAcronym(), "-");
            return measure;
        }

        AlignmentBasedFitness alignmentBasedFitness = new AlignmentBasedFitness();

        for(int i = 0; i < fold; i++) {
            XLog log1 = factory.createLog(log.getAttributes());
            for (int j = 0; j < fold; j++) {
                if (j != i) {
                    log1.addAll(logs[j]);
                }
            }

            try {
                petrinetWithMarking = miningAlgorithm.minePetrinet(pluginContext, log1, false, null, xEventClassifier);
                Double f = alignmentBasedFitness.computeMeasurement(pluginContext, xEventClassifier, petrinetWithMarking, miningAlgorithm, logs[i]).getValue();
                fitness += (f != null)?f:0.0;
                System.out.println("DEBUG - " + (i+1) + "/" + fold + " -fold fitness: " + f);
            } catch( Exception e ) { return measure; }
        }

        measure.setValue(fitness / (double) fold);
        return measure;
    }

    @Override
    public String getMeasurementName() {
        return fold+"-Fold Alignment-Based Fitness";
    }

    @Override
    public String getAcronym() { return "(a)("+fold+"-f)fit."; }

    private XLog[] createdXFolds() {

        if(log.size() < fold) fold = log.size();
        XLog[] logs = new XLog[fold];

        for(int i = 0; i < fold; i++) {
            logs[i] = factory.createLog(log.getAttributes());
        }

        if(log.size() == fold) {
            int pos = 0;
            for (XTrace t : log) {
                logs[pos].add(t);
                pos++;
            }
        }else {
            boolean finish = false;
            while (!finish) {
                finish = true;
                for (XTrace t : log) {
                    int pos = r.nextInt(fold);
                    logs[pos].add(t);
                }
                for (int i = 0; i < logs.length; i++) {
                    if (logs[i].size() == 0) {
                        finish = false;
                    }
                }
                if(!finish) {
                    for(int i = 0; i < fold; i++) {
                        logs[i].clear();
                    }
                }
            }
        }

        return logs;
    }
}
