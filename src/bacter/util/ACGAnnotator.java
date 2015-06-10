/*
 * Copyright (C) 2015 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package bacter.util;

import bacter.ConversionGraph;
import bacter.Locus;
import beast.app.treeannotator.CladeSystem;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Font;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
public class ACGAnnotator {

    public enum HeightStrategy { MEAN, MEDIAN }

    static class ACGAnnotatorOptions {
        public File inFile;
        public File outFile = new File("summary.tree");
        public double burninPercentage = 10.0;
        public HeightStrategy heightStrategy = HeightStrategy.MEAN;
    }

    public ACGAnnotator(ACGAnnotatorOptions options) throws Exception {

        LogFileReader logReader = new LogFileReader(options.inFile);
        ConversionGraph acg = new ConversionGraph();
        for (Locus locus : logReader.getLoci())
                acg.lociInput.setValue(locus, acg);
        acg.initAndValidate();

        // Count trees
        int nTrees=0;
        while (logReader.getNextTreeString() != null)
            nTrees += 1;

        int burnin = (int)Math.round(options.burninPercentage*nTrees/100.0);

        System.out.println(nTrees + " trees in file.");

        CladeSystem cladeSystem = new CladeSystem();

        logReader.reset();
        int treeIdx = 0;
        while (true) {
            String nextTreeString = logReader.getNextTreeString();
            if (nextTreeString == null)
                break;

            if (treeIdx<burnin) {
                treeIdx += 1;
                continue;
            }

            acg.fromExtendedNewick(nextTreeString);
            cladeSystem.add(acg,false);

            treeIdx += 1;
        }

        cladeSystem.calculateCladeCredibilities(nTrees-burnin);

        // 1. Identify MCC CF topology

        // 2. Summarize CF node heights

    }

    class LogFileReader {
        File logFile;
        BufferedReader reader;

        List<String> preamble;
        String nextLine;

        List<Locus> loci;

        public LogFileReader(File logFile) throws IOException {
            this.logFile = logFile;

            reader = new BufferedReader(new FileReader(logFile));
            preamble = new ArrayList<>();

            skipPreamble();

            loci = new ArrayList<>();
            extractLociFromPreamble();
        }

        private void skipPreamble() throws IOException {
            boolean recordPreamble = preamble.isEmpty();

            while(true) {
                nextLine = reader.readLine();

                if (nextLine == null)
                    throw new IOException("Reached end of file while searching for first tree.");

                if (nextLine.toLowerCase().startsWith("tree"))
                    break;

                if (recordPreamble)
                    preamble.add(nextLine);
            }
        }

        private void extractLociFromPreamble() {
            for (String line : preamble) {
                line = line.trim();
                if (line.startsWith("loci ") && line.endsWith(";")) {
                    for (String locusEntry : line.substring(5,line.length()-1).split(" ")) {
                        String[] locusPair = locusEntry.split(":");
                        loci.add(new Locus(locusPair[0], Integer.parseInt(locusPair[1])));
                    }
                }
            }
        }

        public void reset() throws IOException {
            reader.close();
            reader = new BufferedReader(new FileReader(logFile));
            skipPreamble();
        }

        public String getNextTreeString() throws IOException {
            StringBuilder sb = new StringBuilder();

            while (true) {
                if (nextLine == null)
                    return null;

                sb.append(nextLine.trim());
                if (nextLine.trim().endsWith(";"))
                    break;

                nextLine = reader.readLine();
            }
            nextLine = reader.readLine();

            String treeString = sb.toString();

            return treeString.substring(treeString.indexOf("("));
        }

        public List<Locus> getLoci() {
            return loci;
        }

    }


    private static ACGAnnotatorOptions getOptionsGUI() {

        ACGAnnotatorOptions options = new ACGAnnotatorOptions();
        boolean[] canceled = {false};

        JDialog dialog = new JDialog((JDialog)null, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLocationRelativeTo(null);
        dialog.setTitle("ACGAnnotator");

        JLabel logFileLabel = new JLabel("ACG log file:");
        JLabel outFileLabel = new JLabel("Output file:");
        JLabel burninLabel = new JLabel("Burn-in percentage:");
        JLabel heightMethodLabel = new JLabel("Node height method:");

        JTextField inFilename = new JTextField(20);
        inFilename.setEditable(false);
        JButton inFileButton = new JButton("Choose File");

        JTextField outFilename = new JTextField(20);
        outFilename.setText(options.outFile.getName());
        outFilename.setEditable(false);
        JButton outFileButton = new JButton("Choose File");

        JSlider burninSlider = new JSlider(JSlider.HORIZONTAL,
                0, 100, 10);
        burninSlider.setMajorTickSpacing(50);
        burninSlider.setMinorTickSpacing(10);
        burninSlider.setPaintTicks(true);
        burninSlider.setPaintLabels(true);

        JComboBox<HeightStrategy> heightMethodCombo = new JComboBox<>(HeightStrategy.values());

        Container cp = dialog.getContentPane();
        BoxLayout boxLayout = new BoxLayout(cp, BoxLayout.PAGE_AXIS);
        cp.setLayout(boxLayout);

        JPanel mainPanel = new JPanel();

        GroupLayout layout = new GroupLayout(mainPanel);
        mainPanel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup()
                        .addComponent(logFileLabel)
                        .addComponent(outFileLabel)
                        .addComponent(burninLabel)
                        .addComponent(heightMethodLabel))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                        .addComponent(inFilename)
                        .addComponent(outFilename)
                        .addComponent(burninSlider)
                        .addComponent(heightMethodCombo))
                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING, false)
                        .addComponent(inFileButton)
                        .addComponent(outFileButton)));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup()
                        .addComponent(logFileLabel)
                        .addComponent(inFilename,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE)
                        .addComponent(inFileButton))
                .addGroup(layout.createParallelGroup()
                        .addComponent(outFileLabel)
                        .addComponent(outFilename,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE)
                        .addComponent(outFileButton))
                .addGroup(layout.createParallelGroup()
                        .addComponent(burninLabel)
                        .addComponent(burninSlider,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE))
                .addGroup(layout.createParallelGroup()
                        .addComponent(heightMethodLabel)
                        .addComponent(heightMethodCombo,
                                GroupLayout.PREFERRED_SIZE,
                                GroupLayout.DEFAULT_SIZE,
                                GroupLayout.PREFERRED_SIZE)));

        mainPanel.setBorder(new EtchedBorder());
        cp.add(mainPanel);

        JPanel buttonPanel = new JPanel();

        JButton runButton = new JButton("Analyze");
        runButton.addActionListener((e) -> {
            options.burninPercentage = burninSlider.getValue();
            options.heightStrategy = (HeightStrategy)heightMethodCombo.getSelectedItem();
            dialog.setVisible(false);
        });
        runButton.setEnabled(false);
        buttonPanel.add(runButton);

        JButton cancelButton = new JButton("Quit");
        cancelButton.addActionListener((e) -> {
            dialog.setVisible(false);
            canceled[0] = true;
        });
        buttonPanel.add(cancelButton);

        JFileChooser inFileChooser = new JFileChooser();
        inFileButton.addActionListener(e -> {
            inFileChooser.setDialogTitle("Select ACG log file to summarize");
            if (options.inFile == null)
                inFileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            int returnVal = inFileChooser.showOpenDialog(dialog);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                options.inFile = inFileChooser.getSelectedFile();
                inFilename.setText(inFileChooser.getSelectedFile().getName());
                runButton.setEnabled(true);
            }
        });

        JFileChooser outFileChooser = new JFileChooser();
        outFileButton.addActionListener(e -> {
            outFileChooser.setDialogTitle("Select output file name.");
            if (options.inFile != null)
                outFileChooser.setCurrentDirectory(options.inFile);
            else
                outFileChooser.setCurrentDirectory(new File(System.getProperty("user.dir")));

            outFileChooser.setSelectedFile(options.outFile);
            int returnVal = outFileChooser.showOpenDialog(dialog);

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                options.outFile = outFileChooser.getSelectedFile();
                outFilename.setText(outFileChooser.getSelectedFile().getName());
            }
        });

        cp.add(buttonPanel);

        dialog.pack();
        dialog.setResizable(false);
        dialog.setVisible(true);

        if (canceled[0])
            return null;
        else
            return options;
    }

    private static void setupGUIOutput() {
        JFrame frame = new JFrame();
        frame.setTitle("ACGAnnotator");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JTextArea textArea = new JTextArea(25, 80);
        textArea.setFont(new Font("monospaced", Font.PLAIN, 12));
        textArea.setEditable(false);
        frame.getContentPane().add(new JScrollPane(textArea), BorderLayout.CENTER);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> System.exit(0));
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(closeButton);
        frame.getContentPane().add(buttonPanel, BorderLayout.PAGE_END);

        // Redirect streams to output window:
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                textArea.append(String.valueOf((char)b));
            }
        };

        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));

        frame.pack();
        frame.setVisible(true);
    }

    public static String helpMessage =
            "ACGAnnotator - produces summaries of Bacter ACG log files.\n"
                    + "\n"
                    + "Usage: appstore ACGAnnotator [-help | [options] logFile [outputFile]\n"
                    + "\n"
                    + "Option                   Description\n"
                    + "--------------------------------------------------------------\n"
                    + "-help                    Display usage info.\n"
                    + "-heights {mean,median}   Choose node height method.\n"
                    + "-burnin percentage       Choose _percentage_ of log to discard\n"
                    + "                         in order to remove burn-in period.";

    public static void printUsageAndExit() {
        System.out.println(helpMessage);
        System.exit(0);
    }

    public static void printUsageAndError() {
        System.err.println("Error processing command line parameters.\n");
        System.err.println(helpMessage);
        System.exit(1);
    }

    public static ACGAnnotatorOptions getCLIOptions(String[] args) {
        ACGAnnotatorOptions options = new ACGAnnotatorOptions();

        int i=0;
        while (args[i].startsWith("-")) {
            switch(args[i]) {
                case "-help":
                    printUsageAndExit();
                    break;

                case "-burnin":
                    if (args.length<=i+1)
                        printUsageAndError();

                    try {
                        options.burninPercentage = Double.parseDouble(args[i+1]);
                    } catch (NumberFormatException e) {
                        printUsageAndError();
                    }

                    if (options.burninPercentage<0 || options.burninPercentage>100)
                        printUsageAndError();

                    i += 1;
                    break;

                case "-heights":
                    if (args.length<=i+1)
                        printUsageAndError();

                    if (args[i+1].toLowerCase().equals("mean")) {
                        options.heightStrategy = HeightStrategy.MEAN;

                        i += 1;
                        break;
                    }

                    if (args[i+1].toLowerCase().equals("median")) {
                        options.heightStrategy = HeightStrategy.MEDIAN;

                        i += 1;
                        break;
                    }

                    printUsageAndError();

                default:
                    printUsageAndError();
            }

            i += 1;
        }

        if (i >= args.length)
            printUsageAndError();
        else
            options.inFile = new File(args[i]);

        if (i+1<args.length)
            options.outFile = new File(args[i+1]);

        return options;
    }

    /**
     * Main method for ACGAnnotator.  Sets up GUI if needed then
     * uses the ACGAnnotator constructor to actually perform the analysis.
     *
     * @param args
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            // Retrieve options from GUI:
            SwingUtilities.invokeLater(() -> {
                ACGAnnotatorOptions options = getOptionsGUI();

                if (options == null)
                    System.exit(0);

                setupGUIOutput();

                // Run ACGAnnotator
                try {
                    new ACGAnnotator(options);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(null, e.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            });

        } else {

            // Run ACGAnnotator
            try {
                new ACGAnnotator(getCLIOptions(args));
            } catch (Exception e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
        }

    }
}
