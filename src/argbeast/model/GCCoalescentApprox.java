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

import argbeast.Recombination;
import argbeast.RecombinationGraph;
import beast.core.Description;
import beast.core.Input;
import beast.core.State;
import beast.core.parameter.RealParameter;
import beast.evolution.tree.coalescent.Coalescent;
import beast.evolution.tree.coalescent.PopulationFunction;
import beast.evolution.tree.coalescent.TreeIntervals;
import feast.input.In;
import java.util.List;
import java.util.Random;

/**
 * @author Tim Vaughan <tgvaughan@gmail.com>
 */
@Description("Appoximation to the coalescent with gene conversion.")
public class GCCoalescentApprox extends RecombinationGraphDistribution {
    
    public Input<RecombinationGraph> argInput = new In<RecombinationGraph>(
            "arg", "Recombination graph.").setRequired();
    
    public Input<PopulationFunction> popSizeInput = new In<PopulationFunction>(
            "populationModel", "Population model.").setRequired();
    
    public Input<RealParameter> rhoInput = new In<RealParameter>("rho",
            "Recombination rate parameter.").setRequired();
    
    public Input<RealParameter> deltaInput = new In<RealParameter>("delta",
            "Tract length parameter.").setRequired();
    
    public Input<Boolean> allowSECInput = new In<Boolean>(
            "allowSameEdgeCoalescence",
            "Allow recombinant edges to attach to the edge they depart from.")
            .setDefault(true);

    
    RecombinationGraph arg;
    PopulationFunction popSize;
    int sequenceLength;
    
    boolean allowSEC;

    @Override
    public void initAndValidate() throws Exception {

        arg = argInput.get();
        
        sequenceLength = arg.getSequenceLength();

        popSize = popSizeInput.get();
        
        allowSEC = allowSECInput.get();
        
        super.initAndValidate();
    }
    
    @Override
    public double calculateLogP() throws Exception {
        
        logP = 0.0;
        
        for (Recombination recomb : arg.getRecombinations()) {
            if (recomb == null)
                logP += calculateClonalFrameLogP();
            else
                logP += calculateRecombinantLogP(recomb);
        }
        
        logP += calculateConvertedRegionMapLogP();
        
        return logP;        
    }

    /**
     * Compute probability of clonal frame under coalescent.
     * 
     * @return log(P)
     */
    public double calculateClonalFrameLogP() {
        
        List<RecombinationGraph.Event> events = arg.getCFEvents();
        
        double thisLogP = 0.0;
        
        for (int i=0; i<events.size()-1; i++) {
            double timeA = events.get(i).getHeight();
            double timeB = events.get(i+1).getHeight();

            double intervalArea = popSize.getIntegral(timeA, timeB);
            int k = events.get(i).getLineageCount();
            thisLogP += -0.5*k*(k-1)*intervalArea;
            
            if (events.get(i+1).getType()==RecombinationGraph.EventType.COALESCENCE)
                thisLogP += -Math.log(1.0/popSize.getPopSize(timeB));
        }
        
        return thisLogP;
    }
    
    /**
     * Compute probability of recombinant edges under conditional coalescent.
     * @param recomb
     * @return log(P)
     */
    public double calculateRecombinantLogP(Recombination recomb) {
        
        List<RecombinationGraph.Event> events = arg.getCFEvents();
        
        // Probability density of location of recombinant edge start
        double thisLogP = -Math.log(arg.getClonalFrameLength());

        // Identify interval containing the start of the recombinant edge
        int startIdx = 0;
        while (startIdx < events.size()-1 &&
                events.get(startIdx+1).getHeight() < recomb.getHeight1()) {
            startIdx += 1;
        }
        
        // Lineages with which recombinant lineage can coalesce before
        // this time = k(t)-1, while after this time = k(t).
        double oldCoalescenceTime = recomb.getNode1().getParent().getHeight();
        
        for (int i=startIdx; events.get(i).getHeight()<recomb.getHeight2(); i++) {
            
            double timeA = Math.max(events.get(i).getHeight(), recomb.getHeight1());
            
            double timeB;
            int k;
            if (i<events.size()-1) {
                timeB = Math.min(recomb.getHeight2(), events.get(i+1).getHeight());
                k = events.get(i).getLineageCount();
                
                if (!allowSEC && events.get(i).getHeight() < oldCoalescenceTime)
                    k -=1;
            } else {
                timeB = recomb.getHeight2();
                k = 1;
            }
            
            double intervalArea = popSize.getIntegral(timeA, timeB);
            thisLogP += -k*intervalArea;
        }
        
        // Probability of single coalescence event
        thisLogP += -Math.log(popSize.getPopSize(recomb.getHeight2()));
        
        return thisLogP;
    }
    
    /**
     * Compute probability of number and genome extent of converted segments.
     * @return log(P)
     */
    public double calculateConvertedRegionMapLogP() {
        
        double thisLogP = 0.0;
        
        List<Recombination> recombs = arg.getRecombinations();
        
        double rho = rhoInput.get().getValue();
        double pTractEnd = 1.0/deltaInput.get().getValue();
        double cfLength = arg.getClonalFrameLength();
        
        // Probability of recombination per site along sequence
        double pRec = 0.5*rho*cfLength/sequenceLength;
        
        // Probability that sequence begins in the clonal frame:
        double pStartCF = 1.0/(pRec/pTractEnd + 1.0);
        
        if (arg.getNRecombs()==0){
            // Probability of no recombinations
            thisLogP += Math.log(pStartCF) 
                    + (sequenceLength-1)*Math.log(1.0-pRec);
        } else {
            
            // Contribution from start of sequence up to first recomb region
            if (recombs.get(1).getStartLocus()>0) {
                thisLogP += Math.log(pStartCF)
                        + (recombs.get(1).getStartLocus()-1)*Math.log(1-pRec);
            }  else {
                thisLogP += Math.log(1.0-pStartCF)
                        - Math.log(pRec);
            }
            
            // Contribution from remaining recomb regions and adjacent CF regions
            for (int ridx=1; ridx<recombs.size(); ridx++) {
                Recombination recomb = recombs.get(ridx);
                
                thisLogP += Math.log(pRec)
                        + (recomb.getEndLocus() - recomb.getStartLocus())*Math.log(1.0-pTractEnd);
                
                if (ridx<recombs.size()-1) {
                    thisLogP += Math.log(pTractEnd)
                            + (recombs.get(ridx+1).getStartLocus()-recomb.getEndLocus()-2)
                            *Math.log(1.0-pRec);
                } else {
                    if (recomb.getEndLocus()<sequenceLength-1) {
                        thisLogP += Math.log(pTractEnd)
                                + (sequenceLength-1-recomb.getEndLocus()-1)
                                *Math.log(1.0-pRec);
                    }
                }
            }
        }
        
        
        return thisLogP;
    }

    @Override
    protected boolean requiresRecalculation() {
        return true;
    }

    @Override
    public List<String> getArguments() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<String> getConditions() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void sample(State state, Random random) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
