/*
 * Encog(tm) Core v3.2 - Java Version
 * http://www.heatonresearch.com/encog/
 * http://code.google.com/p/encog-java/
 
 * Copyright 2008-2012 Heaton Research, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *   
 * For more information on Heaton Research copyrights, licenses 
 * and trademarks visit:
 * http://www.heatonresearch.com/copyright
 */
package org.encog.neural.neat.training;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.encog.mathutil.randomize.RandomChoice;
import org.encog.mathutil.randomize.RangeRandomizer;
import org.encog.ml.MLContext;
import org.encog.ml.MLMethod;
import org.encog.ml.TrainingImplementationType;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.ea.genome.Genome;
import org.encog.ml.ea.score.GeneticScoreAdapter;
import org.encog.ml.ea.sort.MinimizeAdjustedScoreComp;
import org.encog.ml.ea.sort.MinimizeScoreComp;
import org.encog.ml.ea.train.basic.BasicEA;
import org.encog.ml.train.MLTrain;
import org.encog.ml.train.strategy.Strategy;
import org.encog.neural.neat.NEATNetwork;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.NEATSpecies;
import org.encog.neural.neat.training.opp.NEATCrossover;
import org.encog.neural.networks.training.CalculateScore;
import org.encog.neural.networks.training.TrainingError;
import org.encog.neural.networks.training.propagation.TrainingContinuation;

/**
 * Implements NEAT genetic training.
 * 
 * NeuroEvolution of Augmenting Topologies (NEAT) is a genetic algorithm for the
 * generation of evolving artificial neural networks. It was developed by Ken
 * Stanley while at The University of Texas at Austin.
 * 
 * http://www.cs.ucf.edu/~kstanley/
 * 
 */
public class NEATTraining extends BasicEA implements MLTrain {

	/**
	 * The average fit adjustment.
	 */
	private double averageFitAdjustment;

	/**
	 * The best ever score.
	 */
	private double bestEverScore;

	/**
	 * The best ever network.
	 */
	private NEATNetwork bestEverNetwork;

	/**
	 * The number of inputs.
	 */
	private final int inputCount;

	/**
	 * The number of output neurons.
	 */
	private final int outputCount;

	/**
	 * The total fit adjustment.
	 */
	private double totalFitAdjustment;

	/**
	 * The iteration number.
	 */
	private int iteration;
	
	/**
	 * The parameters of NEAT.
	 */
	private final NEATParams params = new NEATParams();
	
	private RandomChoice mutateChoices;
	
	private RandomChoice mutateAddChoices;
		
	private NEATPopulation population;
	
	private NEATCrossover crossover;

	/**
	 * Construct a neat trainer with a new population. The new population is
	 * created from the specified parameters.
	 * 
	 * @param calculateScore
	 *            The score calculation object.
	 * @param inputCount
	 *            The input neuron count.
	 * @param outputCount
	 *            The output neuron count.
	 * @param populationSize
	 *            The population size.
	 */
	public NEATTraining(final CalculateScore calculateScore,
			final int inputCount, final int outputCount,
			final int populationSize) {
		super(new NEATPopulation(inputCount, outputCount,
				populationSize), new GeneticScoreAdapter(calculateScore));

		this.inputCount = inputCount;
		this.outputCount = outputCount;

		setBestComparator(new MinimizeScoreComp());
		setSelectionComparator(new MinimizeAdjustedScoreComp());

		init();
	}

	/**
	 * Construct neat training with an existing population.
	 * 
	 * @param calculateScore
	 *            The score object to use.
	 * @param population
	 *            The population to use.
	 */
	public NEATTraining(final CalculateScore calculateScore,
			final NEATPopulation population) {
		super(population,new GeneticScoreAdapter(calculateScore));
		
		if (population.size() < 1) {
			throw new TrainingError("Population can not be empty.");
		}
		
		final NEATGenome genome = (NEATGenome) population.getGenomes().get(0);
		setBestComparator(new MinimizeScoreComp());
		setSelectionComparator(new MinimizeAdjustedScoreComp());
		setPopulation(population);
		this.inputCount = genome.getInputCount();
		this.outputCount = genome.getOutputCount();
		init();
	}


	/**
	 * Not supported, will throw an error.
	 * 
	 * @param strategy
	 *            Not used.
	 */
	@Override
	public void addStrategy(final Strategy strategy) {
		throw new TrainingError(
				"Strategies are not supported by this training method.");
	}

	/**
	 * Adjust the species compatibility threshold. This prevents us from having
	 * too many species.
	 */
	public void adjustCompatibilityThreshold() {

		// has this been disabled (unlimited species)
		if (this.params.maxNumberOfSpecies < 1) {
			return;
		}

		final double thresholdIncrement = 0.01;

		if (getPopulation().getSpecies().size() > this.params.maxNumberOfSpecies) {
			this.params.compatibilityThreshold += thresholdIncrement;
		}

		else if (getPopulation().getSpecies().size() < 2) {
			this.params.compatibilityThreshold -= thresholdIncrement;
		}

	}

	/**
	 * Adjust each species score.
	 */
	public void adjustSpeciesScore() {
		for (final NEATSpecies s : getPopulation().getSpecies()) {
			// loop over all genomes and adjust scores as needed
			for (final Genome member : s.getMembers()) {
				double score = member.getScore();

				// apply a youth bonus
				if (s.getAge() < getPopulation().getYoungBonusAgeThreshold()) {
					score = getSelectionComparator().applyBonus(score,
							getPopulation().getYoungScoreBonus());
				}

				// apply an old age penalty
				if (s.getAge() > getPopulation().getOldAgeThreshold()) {
					score = getSelectionComparator().applyPenalty(score,
							getPopulation().getOldAgePenalty());
				}

				final double adjustedScore = score / s.getMembers().size();

				member.setAdjustedScore(adjustedScore);

			}
		}
	}

	@Override
	public boolean canContinue() {
		return false;
	}
	

	/**
	 * Called when training is done.
	 */
	@Override
	public void finishTraining() {

	}

	/**
	 * return The error for the best genome.
	 */
	@Override
	public double getError() {
		return this.bestEverScore;
	}

	@Override
	public TrainingImplementationType getImplementationType() {
		return TrainingImplementationType.Iterative;
	}

	/**
	 * @return The innovations.
	 */
	public NEATInnovationList getInnovations() {
		return (NEATInnovationList) getPopulation().getInnovations();
	}

	/**
	 * @return The input count.
	 */
	public int getInputCount() {
		return this.inputCount;
	}

	@Override
	public int getIteration() {
		return this.iteration;
	}

	/**
	 * @return A network created for the best genome.
	 */
	@Override
	public MLMethod getMethod() {
		return this.bestEverNetwork;
	}

	/**
	 * @return The number of output neurons.
	 */
	public int getOutputCount() {
		return this.outputCount;
	}

	/**
	 * Returns an empty list, strategies are not supported.
	 * 
	 * @return The strategies in use(none).
	 */
	@Override
	public List<Strategy> getStrategies() {
		return new ArrayList<Strategy>();
	}

	/**
	 * Returns null, does not use a training set, rather uses a score function.
	 * 
	 * @return null, not used.
	 */
	@Override
	public MLDataSet getTraining() {
		return null;
	}

	/**
	 * setup for training.
	 */
	private void init() {
		this.mutateChoices = new RandomChoice(new double[] {0.988, 0.001, 0.01, 0.0, 0.001 } );
		this.mutateAddChoices = new RandomChoice(new double[] {0.988, 0.001, 0.01, 0.0 } );
		
		this.crossover = new NEATCrossover();
		this.crossover.init(this);
		
		if (this.getScoreFunction().shouldMinimize()) {
			this.bestEverScore = Double.MAX_VALUE;
		} else {
			this.bestEverScore = Double.MIN_VALUE;
		}

		// check the population
		for (final Genome obj : getPopulation().getGenomes()) {
			if (!(obj instanceof NEATGenome)) {
				throw new TrainingError(
						"Population can only contain objects of NEATGenome.");
			}

			final NEATGenome neat = (NEATGenome) obj;

			if ((neat.getInputCount() != this.inputCount)
					|| (neat.getOutputCount() != this.outputCount)) {
				throw new TrainingError(
						"All NEATGenome's must have the same input and output sizes as the base network.");
			}
		}

		resetAndKill();
		sortAndRecord();
		speciateAndCalculateSpawnLevels();
	}

	/**
	 * @return True if training can progress no further.
	 */
	@Override
	public boolean isTrainingDone() {
		return false;
	}

	/**
	 * Perform one training iteration.
	 */
	@Override
	public void iteration() {
		NEATGenome[] parents = new NEATGenome[2];
		NEATGenome[] children = new NEATGenome[1];
		
		Random rnd = new Random();
		
		this.iteration++;
		final List<NEATGenome> newPop = new ArrayList<NEATGenome>();

		int numSpawnedSoFar = 0;

		for (final NEATSpecies s : getPopulation().getSpecies()) {
			if (numSpawnedSoFar < getPopulation().size()) {
				int numToSpawn = (int) Math.round(s.getNumToSpawn());

				boolean bChosenBestYet = false;

				while ((numToSpawn--) > 0) {
					children[0] = null;

					if (!bChosenBestYet) {
						children[0] = (NEATGenome) s.getLeader();

						bChosenBestYet = true;
					}

					else {
						// if the number of individuals in this species is only
						// one
						// then we can only perform mutation
						if (s.getMembers().size() == 1) {
							// spawn a child
							children[0] = new NEATGenome((NEATGenome) s.chooseParent());
						} else {
							parents[0] = (NEATGenome) s.chooseParent();

							if (Math.random() < this.params.crossoverRate) {
								parents[1] = (NEATGenome) s.chooseParent();

								int numAttempts = 5;

								while ((parents[0].getGenomeID() == parents[1].getGenomeID())
										&& ((numAttempts--) > 0)) {
									parents[1] = (NEATGenome) s.chooseParent();
								}

								if (parents[0].getGenomeID() != parents[1].getGenomeID()) {
									this.crossover.performOperation(rnd, parents, 0, children, 0);
								}
							}

							else {
								children[0] = new NEATGenome(parents[0]);
							}
						}

						if (children[0] != null) {
							children[0].setGenomeID(getPopulation().assignGenomeID());
							mutate(children[0]);
						}
					}

					if (children[0] != null) {
						// sort the baby's genes by their innovation numbers
						children[0].sortGenes();

						// add to new pop
						// if (newPop.contains(baby)) {
						// throw new EncogErrorthis.params("readd");
						// }
						newPop.add(children[0]);

						++numSpawnedSoFar;

						if (numSpawnedSoFar == getPopulation().size()) {
							numToSpawn = 0;
						}
					}
				}
			}
		}

		while (newPop.size() < getPopulation().size()) {
			newPop.add(tournamentSelection(getPopulation().size() / 5));
		}

		getPopulation().clear();
		getPopulation().addAll(newPop);

		resetAndKill();
		sortAndRecord();
		speciateAndCalculateSpawnLevels();
	}
	
	public void mutate(NEATGenome genome) {
		int option = this.mutateChoices.generate(new Random());
		
		switch(option) {
			case 0: // mutate weight
				genome.mutateWeights(this.params.mutationRate,
						this.params.probabilityWeightReplaced,
						this.params.maxWeightPerturbation);
				break;
			case 1: // add node
				if (genome.getNeuronsChromosome().size() < this.params.maxPermittedNeurons) {
					genome.addNeuron(this, this.params.chanceAddNode,
							this.params.numTrysToFindOldLink);
				}
				break;
			case 2: // add connection
				// now there's the chance a link may be added
				genome.addLink(	this, this.params.numTrysToFindLoopedLink,
						this.params.numAddLinkAttempts);
				break;
			case 3: // adjust curve
				break;
			case 4: // remove connection
				genome.removeLink();
				break;
		}
	}

	/**
	 * Perform the specified number of training iterations. This is a basic
	 * implementation that just calls iteration the specified number of times.
	 * However, some training methods, particularly with the GPU, benefit
	 * greatly by calling with higher numbers than 1.
	 * 
	 * @param count
	 *            The number of training iterations.
	 */
	@Override
	public void iteration(final int count) {
		for (int i = 0; i < count; i++) {
			iteration();
		}
	}

	@Override
	public TrainingContinuation pause() {
		return null;
	}

	/**
	 * Reset for an iteration.
	 */
	public void resetAndKill() {
		this.totalFitAdjustment = 0;
		this.averageFitAdjustment = 0;

		final Object[] speciesArray = getPopulation().getSpecies().toArray();

		for (final Object element : speciesArray) {
			final NEATSpecies s = (NEATSpecies) element;
			s.purge();

			// did the leader die?  If so, disband the species.
			if( !getPopulation().getGenomes().contains(s.getLeader())) {
				getPopulation().getSpecies().remove(s);
			}
			else if ((s.getGensNoImprovement() > this.params.numGensAllowedNoImprovement)
					&& getSelectionComparator().isBetterThan(this.bestEverScore,
							s.getBestScore())) {
				getPopulation().getSpecies().remove(s);
			}
		}
	}

	@Override
	public void resume(final TrainingContinuation state) {

	}

	/**
	 * Not used.
	 * 
	 * @param error
	 *            Not used.
	 */
	@Override
	public void setError(final double error) {
	}

	@Override
	public void setIteration(final int iteration) {
		this.iteration = iteration;
	}

	/**
	 * Sort the genomes.
	 */
	public void sortAndRecord() {

		for (final Genome genome : getPopulation().getGenomes()) {
			genome.decode();
			calculateScore(genome);
		}

		getPopulation().sort(this.getBestComparator() );

		final Genome genome = getPopulation().get(0);
		final double currentBest = genome.getScore();

		if (getSelectionComparator().isBetterThan(currentBest, this.bestEverScore)) {
			this.bestEverScore = currentBest;
			this.bestEverNetwork = ((NEATNetwork) genome.getOrganism());
		}

		if( getSelectionComparator().isBetterThan(getError(), this.bestEverScore) ) {
			this.bestEverScore = getError();
		}
		
	}

	/**
	 * Determine the species.
	 */
	public void speciateAndCalculateSpawnLevels() {

		// calculate compatibility between genomes and species
		adjustCompatibilityThreshold();

		// assign genomes to species (if any exist)
		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			boolean added = false;

			for (final NEATSpecies s : getPopulation().getSpecies()) {
				final double compatibility = genome
						.getCompatibilityScore((NEATGenome) s.getLeader());

				if (compatibility <= this.params.compatibilityThreshold) {
					addSpeciesMember(s, genome);
					genome.setSpeciesID(s.getSpeciesID());
					added = true;
					break;
				}
			}

			// if this genome did not fall into any existing species, create a
			// new species
			if (!added) {
				getPopulation().getSpecies().add(
						new NEATSpecies(getPopulation(), genome,
								getPopulation().assignSpeciesID()));
			}
		}

		adjustSpeciesScore();

		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			this.totalFitAdjustment += genome.getAdjustedScore();
		}

		this.averageFitAdjustment = this.totalFitAdjustment
				/ getPopulation().size();

		for (final Genome g : getPopulation().getGenomes()) {
			final NEATGenome genome = (NEATGenome) g;
			final double toSpawn = genome.getAdjustedScore()
					/ this.averageFitAdjustment;
			genome.setAmountToSpawn(toSpawn);
		}

		for (final NEATSpecies species : getPopulation().getSpecies()) {
			species.calculateSpawnAmount();
		}
	}

	/**
	 * Select a gene using a tournament.
	 * 
	 * @param numComparisons
	 *            The number of compares to do.
	 * @return The chosen genome.
	 */
	public NEATGenome tournamentSelection(final int numComparisons) {
		double bestScoreSoFar = 0;

		int ChosenOne = 0;

		for (int i = 0; i < numComparisons; ++i) {
			final int ThisTry = (int) RangeRandomizer.randomize(0,
					getPopulation().size() - 1);

			if (getPopulation().get(ThisTry).getScore() > bestScoreSoFar) {
				ChosenOne = ThisTry;

				bestScoreSoFar = getPopulation().get(ThisTry).getScore();
			}
		}

		return (NEATGenome) getPopulation().get(ChosenOne);
	}
	
	/**
	 * Add a genome.
	 * 
	 * @param species
	 *            The species to add.
	 * @param genome
	 *            The genome to add.
	 */
	public void addSpeciesMember(final NEATSpecies species, 
			final NEATGenome genome) {

		if (getSelectionComparator().compare(genome,species.getLeader())<0) {
			species.setBestScore(genome.getScore());
			species.setGensNoImprovement(0);
			species.setLeader(genome);
		}

		species.getMembers().add(genome);

	}

	/**
	 * @return the population
	 */
	public NEATPopulation getPopulation() {
		return population;
	}

	/**
	 * @param population the population to set
	 */
	public void setPopulation(NEATPopulation population) {
		this.population = population;
	}
	
	/**
	 * Calculate the score for this genome. The genome's score will be set.
	 * 
	 * @param g
	 *            The genome to calculate for.
	 */
	public void calculateScore(final Genome g) {
		if (g.getOrganism() instanceof MLContext) {
			((MLContext) g.getOrganism()).clearContext();
		}
		final double score = this.getScoreFunction().calculateScore(g);
		g.setScore(score);
	}
}
