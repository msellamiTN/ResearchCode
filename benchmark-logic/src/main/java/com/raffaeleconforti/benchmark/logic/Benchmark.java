package com.raffaeleconforti.benchmark.logic;

import au.edu.qut.processmining.log.LogParser;
import au.edu.qut.processmining.log.SimpleLog;
import com.raffaeleconforti.context.FakePluginContext;
import com.raffaeleconforti.log.util.LogCloner;
import com.raffaeleconforti.measurements.Measure;
import com.raffaeleconforti.measurements.MeasurementAlgorithm;
import com.raffaeleconforti.measurements.impl.AlignmentBasedFMeasure;
import com.raffaeleconforti.measurements.impl.BPMNComplexity;
import com.raffaeleconforti.wrapper.MiningAlgorithm;
import com.raffaeleconforti.wrapper.PetrinetWithMarking;
import com.raffaeleconforti.wrapper.StructuredMinerAlgorithmWrapperHM52;
import com.raffaeleconforti.wrapper.impl.heuristics.Heuristics52AlgorithmWrapper;
import com.raffaeleconforti.wrapper.impl.inductive.InductiveMinerIMfWrapper;
import hub.top.petrinet.PetriNet;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.in.XesXmlGZIPParser;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.out.XesXmlSerializer;
import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.processmining.acceptingpetrinet.models.impl.AcceptingPetriNetImpl;
import org.processmining.acceptingpetrinet.plugins.ExportAcceptingPetriNetPlugin;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;

import java.io.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.raffaeleconforti.log.util.LogImporter.importFromFile;
import static com.raffaeleconforti.log.util.LogImporter.importFromInputStream;

/**
 * Created by Raffaele Conforti (conforti.raffaele@gmail.com) on 18/10/2016.
 */
public class Benchmark {

    private static final long MAX_TIME = 3600000;

    private boolean defaultLogs;
    private String extLocation;
    private Map<String, Object> inputLogs;
    private LogCloner logCloner = new LogCloner(new XFactoryNaiveImpl());

    private Set<String> packages = new UnifiedSet<>();

    /* this is a multidimensional cube containing all the measures.
    for each log, each mining algorithm and each metric we have a resulting metric value */
    private HashMap<String, HashMap<String, HashMap<String, String>>> measures;

    private Benchmark() {}

    public Benchmark(boolean defaultLogs, String extLocation, Set<String> packages) {
        this.defaultLogs = defaultLogs;
        this.extLocation = extLocation;
        this.packages = packages;
        loadLogs(extLocation);
    }

    public void performBenchmark(ArrayList<Integer> selectedMiners, ArrayList<Integer> selectedMetrics) {

        hub.top.petrinet.PetriNet petriNet = new PetriNet();
        petriNet.getPlaces();

        XEventClassifier xEventClassifier = new XEventNameClassifier();
        FakePluginContext fakePluginContext = new FakePluginContext();

        /* retrieving all the mining algorithms */
        List<MiningAlgorithm> miningAlgorithms = MiningAlgorithmDiscoverer.discoverAlgorithms(packages);
        Collections.sort(miningAlgorithms, new Comparator<MiningAlgorithm>() {
            @Override
            public int compare(MiningAlgorithm o1, MiningAlgorithm o2) {
                return o2.getAlgorithmName().compareTo(o1.getAlgorithmName());
            }
        });

        // pruning the list of miners
        if( selectedMiners != null && !selectedMiners.isEmpty() ) {
//            System.out.println("DEBUG - pruning miners");
            Collections.sort(selectedMiners);
            Collections.reverse(selectedMiners);
            for(int i = miningAlgorithms.size()-1; i >= 0; i--) {
                if( selectedMiners.isEmpty() || (i != selectedMiners.get(0)) ) miningAlgorithms.remove(i);
                else selectedMiners.remove(0);
            }
        }
        System.out.println("DEBUG - total miners: " + miningAlgorithms.size());

        /* retrieving all the measuring algorithms */
        List<MeasurementAlgorithm> measurementAlgorithms = MeasurementAlgorithmDiscoverer.discoverAlgorithms(packages);
        Collections.sort(measurementAlgorithms, new Comparator<MeasurementAlgorithm>() {
            @Override
            public int compare(MeasurementAlgorithm o1, MeasurementAlgorithm o2) {
                return o2.getMeasurementName().compareTo(o1.getMeasurementName());
            }
        });

        // pruning the list of metrics
        if( selectedMetrics != null && !selectedMetrics.isEmpty() ) {
//            System.out.println("DEBUG - pruning metrics");
            Collections.sort(selectedMetrics);
            Collections.reverse(selectedMetrics);
            for(int i = measurementAlgorithms.size()-1; i >= 0; i--) {
                if( selectedMetrics.isEmpty() || (i != selectedMetrics.get(0)) ) measurementAlgorithms.remove(i);
                else selectedMetrics.remove(0);
            }
        }

        System.out.println("DEBUG - total metrics: " + measurementAlgorithms.size());

        measures = new HashMap<>();
        System.out.println("DEBUG - total logs: " + inputLogs.keySet().size());

        /* creating the directory for the results*/
        File resultDir = new File("./results");
        if( !resultDir.exists() && resultDir.mkdir() );


        /* populating measurements results */
        XLog log;
        File old;
        String pathname;
        for( MiningAlgorithm miningAlgorithm : miningAlgorithms ) {
            old = null;

//            if(!(miningAlgorithm instanceof EvolutionaryTreeMinerWrapper)) continue;
            String miningAlgorithmName = miningAlgorithm.getAcronym();
            String measurementAlgorithmName = "NULL";
            System.out.println("DEBUG - mining with algorithm: " + miningAlgorithmName);

            /* creating the directory for the results*/
            File maDir = new File("./results/" + miningAlgorithmName);
            if( !maDir.exists() && maDir.mkdir() );

            for( String logName : inputLogs.keySet() ) {
                log = loadLog(inputLogs.get(logName));
                System.out.println("DEBUG - log: " + logName);
                // adding an entry on the measures table for this miner
                if( !measures.containsKey(miningAlgorithmName) )measures.put(miningAlgorithmName, new HashMap<>());
                measures.get(miningAlgorithmName).put(logName, new HashMap<>());

                try {
                    // mining the petrinet
                    long sTime = System.currentTimeMillis();
                    XLog miningLog = logCloner.cloneLog(log);
                    PetrinetWithMarking petrinetWithMarking = miningAlgorithm.minePetrinet(fakePluginContext, miningLog, false);
                    long execTime = System.currentTimeMillis() - sTime;
                    measures.get(miningAlgorithmName).get(logName).put("_exec-t", Long.toString(execTime));
                    System.out.println("DEBUG - mining time: " + execTime + "ms");

                    String pnpath = "./results/" + miningAlgorithmName + "/" + logName + "_" + Long.toString(System.currentTimeMillis()) + ".pnml";
                    exportPetrinet(fakePluginContext, petrinetWithMarking, pnpath);

                    Measure measure;
                    // computing metrics on the output petrinet
                    for( MeasurementAlgorithm measurementAlgorithm : measurementAlgorithms ) {
                        measurementAlgorithmName = measurementAlgorithm.getAcronym();

                        try {
                            XLog measuringLog = logCloner.cloneLog(log);
                            sTime = System.currentTimeMillis();
                            if(measurementAlgorithm instanceof BPMNComplexity) {
                                BPMNDiagram diagram = miningAlgorithm.mineBPMNDiagram(fakePluginContext, miningLog, false);
                                sTime = System.currentTimeMillis();
                                measure = ((BPMNComplexity) measurementAlgorithm).computeMeasurementBPMN(diagram);
                            } else measure = measurementAlgorithm.computeMeasurement(fakePluginContext, xEventClassifier, petrinetWithMarking, miningAlgorithm, measuringLog);

                            execTime = System.currentTimeMillis() - sTime;
                            if (measurementAlgorithm.isMultimetrics()) {
                                for (String metric : measure.getMetrics()) {
                                    measures.get(miningAlgorithmName).get(logName).put(metric, measure.getMetricValue(metric));
                                    System.out.println("DEBUG - " + metric + " : " + measure.getMetricValue(metric));
                                }
                            } else {
                                measures.get(miningAlgorithmName).get(logName).put(measurementAlgorithmName, String.format("%.2f", measure.getValue()));
                                System.out.println("DEBUG - " + measurementAlgorithmName + " : " + measure.getValue());
                            }

                            if( execTime > MAX_TIME)
                                measures.get(miningAlgorithmName).get(logName).put(measurementAlgorithmName + ":tor", Long.toString(execTime));
                        } catch (Error e) {
                            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
                            e.printStackTrace();
                            measures.get(miningAlgorithmName).get(logName).put(measurementAlgorithmName, "-ERR");
                            System.out.println("ERROR - measuring: " + miningAlgorithmName + " : " + logName + " : " + measurementAlgorithmName);
                        } catch(Exception e) {
                            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
                            System.out.println("ERROR - mining: " + miningAlgorithmName + " - " + measurementAlgorithmName);
                            measures.get(miningAlgorithmName).remove(logName);
                        }
                    }

                } catch(Error e) {
                    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
                    System.out.println("ERROR - mining: " + miningAlgorithmName + " - " + measurementAlgorithmName);
                    e.printStackTrace();
                    measures.get(miningAlgorithmName).remove(logName);
                } catch(Exception e) {
                    System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
                    System.out.println("ERROR - mining: " + miningAlgorithmName + " - " + measurementAlgorithmName);
                    e.printStackTrace();
                    measures.get(miningAlgorithmName).remove(logName);
                }

                if(old != null) old.delete();
                pathname = "./results/" + miningAlgorithmName + "/upto_" + logName + "_" + Long.toString(System.currentTimeMillis()) + ".xls";
                old = new File(pathname);
                publishResults(pathname);
            }
        }
        publishResults("./results/" + "benchmark_" + Long.toString(System.currentTimeMillis()) + ".xls");
    }

    private void loadLogs(String extLocation) {
        inputLogs = new UnifiedMap<>();
        String logName;
        InputStream in;

        try {
            /* Loading first the logs inside the resources folder (default logs) */
            if( defaultLogs ) {
                System.out.println("DEBUG - importing internal logs.");
                ClassLoader classLoader = getClass().getClassLoader();
                String path = "logs/";
                File jarFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath());

                if( jarFile.isFile() ) {
                    JarFile jar = new JarFile(jarFile);
                    Enumeration<JarEntry> entries = jar.entries();
                    while( entries.hasMoreElements() ) {
                        logName = entries.nextElement().getName();
                        if( logName.startsWith(path) && !logName.equalsIgnoreCase(path) ) {
                            System.out.println("DEBUG - found log: " + logName);
                            in = classLoader.getResourceAsStream(logName);
//                            System.out.println("DEBUG - stream size: " + in.available());
                            inputLogs.put(logName.replaceAll(".*/", ""), in);
                        }
                    }
                    jar.close();
                }
            }

            /* checking if the user wants to upload also external logs */
            if( extLocation != null ) {
                System.out.println("DEBUG - importing external logs.");
                File folder = new File(extLocation);
                File[] listOfFiles = folder.listFiles();
                if( folder.isDirectory() ) {
                    for( File file : listOfFiles )
                        if( file.isFile() ) {
                            logName = file.getPath();
                            System.out.println("DEBUG - found log: " + logName);
                            inputLogs.put(file.getName(), logName);
                        }
                } else {
                    System.out.println("ERROR - external logs loading failed, input path is not a folder.");
                }
            }
        } catch( Exception e ) {
            System.out.println("ERROR - something went wrong reading the resource folder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private XLog loadLog(Object o) {
        try {
            if(o instanceof String) {
                return importFromFile(new XFactoryNaiveImpl(), (String) o);
            }else if(o instanceof InputStream){
                return importFromInputStream((InputStream) o, new XesXmlGZIPParser(new XFactoryNaiveImpl()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void exportPetrinet(UIPluginContext context, PetrinetWithMarking petrinetWithMarking, String path) {
        ExportAcceptingPetriNetPlugin exportAcceptingPetriNetPlugin = new ExportAcceptingPetriNetPlugin();
        try {
            exportAcceptingPetriNetPlugin.export(
                    context,
                    new AcceptingPetriNetImpl(petrinetWithMarking.getPetrinet(), petrinetWithMarking.getInitialMarking(), petrinetWithMarking.getFinalMarking()),
                    new File(path));
        } catch (Exception e) {
            System.out.println("ERROR - impossible to export the petrinet to: " + path);
            return;
        }
    }

    private void publishResults(String filename) {
//        System.out.println("DEBUG - starting generation of the excel file.");
        try {
            HSSFWorkbook workbook = new HSSFWorkbook();
            int rowCounter;
            int cellCounter;
            boolean generateHead;

            /* generating one sheet for each log */
            for( String miningAlgorithmName : measures.keySet() ) {
                generateHead = true;
                rowCounter = 0;

                HSSFSheet sheet = workbook.createSheet(miningAlgorithmName);
                sheet.setDefaultColumnWidth(12);
                HSSFRow rowhead = sheet.createRow((short) rowCounter);
                rowCounter++;

                for( String logName : measures.get(miningAlgorithmName).keySet() ) {
                    /* creating the row for this mining algorithm */
                    HSSFRow row = sheet.createRow((short) rowCounter);
                    rowCounter++;

                    cellCounter = 0;
                    if( generateHead ) rowhead.createCell(cellCounter).setCellValue("Log");
                    row.createCell(cellCounter).setCellValue(logName);
                    cellCounter++;

                    ArrayList<String> metrics = new ArrayList<>(measures.get(miningAlgorithmName).get(logName).keySet());
                    Collections.sort(metrics);

                    for( int i = 0; i < metrics.size(); i++ ) {
                        String metricName = metrics.get(i);
                        if( generateHead ) rowhead.createCell(cellCounter).setCellValue(metricName);
                        row.createCell(cellCounter).setCellValue(measures.get(miningAlgorithmName).get(logName).get(metricName));
                        cellCounter++;
                    }
                    generateHead = false;
                }
            }

            FileOutputStream fileOut = new FileOutputStream(filename);
            workbook.write(fileOut);
            fileOut.close();
            System.out.println("DEBUG - results exported to: " + filename);
        } catch ( Exception e ) {
            System.out.println("ERROR - something went wrong while writing the excel sheet: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static void computeFitnessNPrecision(String mLogPath, String eLogPath) {
        XEventClassifier xEventClassifier = new XEventNameClassifier();
        FakePluginContext fakePluginContext = new FakePluginContext();

        AlignmentBasedFMeasure alignmentBasedFMeasure = new AlignmentBasedFMeasure();
        Benchmark benchmark = new Benchmark();

        HashSet<MiningAlgorithm> miningAlgorithms = new HashSet<>();
        miningAlgorithms.add(new Heuristics52AlgorithmWrapper());
        miningAlgorithms.add(new InductiveMinerIMfWrapper());
        miningAlgorithms.add(new StructuredMinerAlgorithmWrapperHM52());

        XLog mLog = benchmark.loadLog(mLogPath);
        XLog eLog = benchmark.loadLog(eLogPath);

        try {
            for( MiningAlgorithm ma : miningAlgorithms ) {

                PetrinetWithMarking petrinet = ma.minePetrinet(fakePluginContext, mLog, false);
                benchmark.exportPetrinet(fakePluginContext, petrinet, "./" + ma.getAcronym() + "_pn.pnml");
                Measure measure = alignmentBasedFMeasure.computeMeasurement(fakePluginContext, xEventClassifier, petrinet, ma, eLog);

                System.out.println("DEBUG - results for: " + ma.getAlgorithmName());
                for( String metric : measure.getMetrics() )
                    System.out.println("RESULT - " + metric + " : " + measure.getMetricValue(metric));
            }
        } catch ( Exception e ) {
            System.out.println("ERROR - " + e.getMessage());
            e.printStackTrace();
            return;
        }

    }

    public static void logsAnalysis(String path) {
        ArrayList<SimpleLog> sLogs = new ArrayList<>();
        Benchmark benchmark = new Benchmark();
        XLog log;

        int totalLogs;

        int avgDistinctTraces = 0;
        int avgDistinctEvents = 0;
        int avgTraces = 0;
        long avgEvents = 0;

        int avgLongestTrace = 0;
        int avgShortestTrace = 0;
        int avgAvgTrace = 0;

        System.out.println("LOGSA - starting analysis ... ");

        benchmark.loadLogs(path);
        for( String logName : benchmark.inputLogs.keySet() ) {
            log = benchmark.loadLog(benchmark.inputLogs.get(logName));
            sLogs.add(LogParser.getSimpleLog(log));
        }

        totalLogs = sLogs.size();


        for( SimpleLog l : sLogs ) {
            avgTraces += l.size();
            avgEvents += l.getTotalEvents();

            avgDistinctTraces += l.getDistinctTraces();
            avgDistinctEvents += l.getDistinctEvents();

            avgShortestTrace += l.getShortestTrace();
            avgAvgTrace += l.getAvgTraceLength();
            avgLongestTrace += l.getLongestTrace();
        }

        System.out.println("LOGSA - analysis result of " + totalLogs + " logs: ");

        System.out.println("LOGSA - avg traces: " + avgTraces/totalLogs);
        System.out.println("LOGSA - avg events: " + avgEvents/totalLogs);

        System.out.println("LOGSA - avg distinct traces: " + avgDistinctTraces/totalLogs);
        System.out.println("LOGSA - avg distinct events: " + avgDistinctEvents/totalLogs);

        System.out.println("LOGSA - avg shortest trace: " + avgShortestTrace/totalLogs);
        System.out.println("LOGSA - avg avg trace: " + avgAvgTrace/totalLogs);
        System.out.println("LOGSA - avg longest trace: " + avgLongestTrace/totalLogs);
    }

    public static void foldLog(String path, String sfold) {
        Benchmark benchmark = new Benchmark();
        XFactory factory = new XFactoryNaiveImpl();
        XLog mLog;
        XLog eLog;
        XLog log = benchmark.loadLog(path);
        Random r = new Random(123456789);

        String mLogPath = sfold + "_mining_fold.xes";
        String eLogPath = sfold + "_eval_fold.xes";
//        String folder = "./folding/";
        String folder = "./";

        FileOutputStream fos;
        XesXmlSerializer serializer = new XesXmlSerializer();

        try {
            int fold = Integer.valueOf(sfold);
            if( log.size() < fold ) fold = log.size();

            /* creating the folds */
            XLog[] logs = new XLog[fold];
            for( int i = 0; i < fold; i++ ) logs[i] = factory.createLog(log.getAttributes());
            if( log.size() == fold ) {
                int pos = 0;
                for( XTrace t : log ) {
                    logs[pos].add(t);
                    pos++;
                }
            } else {
                boolean finish = false;
                while( !finish ) {
                    finish = true;

                    for( XTrace t : log ) {
                        int pos = r.nextInt(fold);
                        logs[pos].add(t);
                    }

                    for( int i = 0; i < logs.length; i++ ) if( logs[i].size() == 0 ) finish = false;
                    if( !finish ) for(int i = 0; i < fold; i++) logs[i].clear();
                }
            }

            /* saving the folds */
            for( int i = 0; i < fold; i++ ) {
                mLog = factory.createLog(log.getAttributes());
                eLog = logs[i];
                for( int j = 0; j < fold; j++ )  if ( j != i )  mLog.addAll(logs[j]);

                fos = new FileOutputStream( new String(folder + (i+1) + "." + mLogPath) );
                serializer.serialize(mLog, fos);
                fos.close();

                fos = new FileOutputStream( new String(folder + (i+1) + "." + eLogPath) );
                serializer.serialize(eLog, fos);
                fos.close();
            }

        } catch(Exception e) {
            System.out.println("ERROR - something went wrong while folding the log.");
            e.printStackTrace();
        }
    }

}
