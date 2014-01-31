/*
 * Copyright (C) 2013 Tim Vaughan <tgvaughan@gmail.com>
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

package argbeast.model;

import argbeast.util.RecombinationGraphStatsLogger;
import beast.core.Description;
import beast.core.Input;
import beast.core.Input.Validate;
import beast.evolution.tree.Tree;
import beast.evolution.tree.coalescent.PopulationFunction;
import java.io.PrintStream;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Simulates an ARG - can be used for chain initialization or for "
        + "sampler validation.")
public class RecombinationGraphSimulator extends beast.core.Runnable {

    public Input<Double> rhoInput = new Input<Double>("rho",
            "Recombination rate parameter.", Validate.REQUIRED);
    
    public Input<Double> deltaInput = new Input<Double>("delta",
            "Tract length parameter.", Validate.REQUIRED);
    
    public Input<PopulationFunction> popFuncInput = new Input<PopulationFunction>(
            "populationModel", "Demographic model to use.", Validate.REQUIRED);
    
    public Input<Integer> sequenceLengthInput = new Input<Integer>(
            "sequenceLength", "Length of sequence to use in simulation."
                    + " (Only use when alignment is not available.)");
    
    public Input<Integer> nTaxaInput = new Input<Integer>(
            "nTaxa", "Number of taxa to use in simulation. "
                    + "(Only use when alignment is unavailable.)");
    
    public Input<Integer> nSimsInput = new Input<Integer>(
            "nSims", "Number of ARGs to simulate.", Validate.REQUIRED);
    
    public Input<String> statsFileNameInput = new Input<String>(
            "statsFileName", "Name of file in which to record statistics.",
            Validate.REQUIRED);

    public Input<Tree> clonalFrameInput = new Input<Tree>(
            "clonalFrame", "Optional tree specifying fixed clonal frame."
    );
    
    @Override
    public void initAndValidate() { }

    @Override
    public void run() throws Exception {

        // Initalize ARG object
        SimulatedRecombinationGraph arg = new SimulatedRecombinationGraph();
        arg.initByName(
                "rho", rhoInput.get(),
                "delta", deltaInput.get(),
                "populationModel", popFuncInput.get(),
                "sequenceLength", sequenceLengthInput.get(),
                "nTaxa", nTaxaInput.get(),
                "clonalFrame", clonalFrameInput.get()
        );
        arg.setID("arg");
        
        PrintStream statFile = new PrintStream(statsFileNameInput.get());

        RecombinationGraphStatsLogger statsLogger = new RecombinationGraphStatsLogger();
        statsLogger.initByName("arg", arg);

        statsLogger.init(statFile);
        statFile.println();
        
        for (int i=0; i<nSimsInput.get(); i++) {
            arg.initAndValidate();
            statsLogger.log(i, statFile);
            statFile.println();
            
//            System.out.println(arg.toString());
        }
        
        statFile.close();
    }
}