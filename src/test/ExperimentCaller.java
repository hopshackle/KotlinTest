package test;
import games.simplegridgame.Experiment;

public class ExperimentCaller {
    public static void main(String[] args){
        int updateRule = Integer.parseInt(System.getProperty("updateRule", "0"));
        int agent = Integer.parseInt(System.getProperty("agent", "0"));
        boolean trueModel = Boolean.parseBoolean(System.getProperty("trueModel", "false"));

        int learnSteps = Integer.parseInt(System.getProperty("learnSteps", "3"));
        int testSteps = Integer.parseInt(System.getProperty("testSteps", "100"));
        int gamesPerEval = Integer.parseInt(System.getProperty("gamesPerEval", "1"));
        int nPredictionTests = Integer.parseInt(System.getProperty("nPredictionTests", "1"));
        int w = Integer.parseInt(System.getProperty("w", "30"));
        int h = Integer.parseInt(System.getProperty("h", "30"));
        boolean visual = Boolean.parseBoolean(System.getProperty("visual", "true"));
        int lutSizeLimit =   Integer.parseInt(System.getProperty("lutSizeLimit", "0"));
        boolean diceRoll = Boolean.parseBoolean(System.getProperty("diceRoll", "false"));
        int nReps =   Integer.parseInt(System.getProperty("nReps", "5"));
        int lutSize =   Integer.parseInt(System.getProperty("lutSize", "1"));


        String outFileName="results_updateRule_"+updateRule+ "_agent_"+agent+"_trueModel_"+trueModel + "_learnSteps_"+ learnSteps + "_testSteps_"+testSteps+"_gamesPerEval_"+gamesPerEval+"_nPredictionTests_"+
                nPredictionTests+"_w_"+w+"_h_"+h+"_visual_"+visual+"_lutSizeLimit_"+lutSizeLimit+"_diceRoll_"+diceRoll+"_nReps_"+nReps+"_lutSize_"+
                lutSize + ".txt";

        Experiment exp = new Experiment(updateRule, agent, trueModel, learnSteps, testSteps, gamesPerEval, nPredictionTests, w, h, visual,
                lutSizeLimit, diceRoll, nReps, lutSize, outFileName);
        exp.run();

    }
}
