package agents

import ggi.AbstractGameState
import ggi.ActionAbstractGameState
import ggi.SimplePlayerInterface

import java.util.Random

data class SimpleEvoAgent(
        var flipAtLeastOneValue: Boolean = true,
        // var expectedMutations: Double = 10.0,
        var probMutation: Double = 0.2,
        var sequenceLength: Int = 200,
        var nEvals: Int = 20,
        var useShiftBuffer: Boolean = true,
        var useMutationTransducer: Boolean = true,
        var repeatProb: Double = 0.5,  // only used with mutation transducer
        var discountFactor: Double? = null,
        var opponentModel: SimplePlayerInterface = DoNothingAgent()
) : SimplePlayerInterface {
    override fun getAgentType(): String {
        return "SimpleEvoAgent"
    }

    internal var random = Random()

    // these are all the parameters that control the agend
    internal var buffer: IntArray? = null // randomPoint(sequenceLength)

    // SimplePlayerInterface opponentModel = new RandomAgent();
    override fun reset(): SimplePlayerInterface {
        // buffer = null
        return this
    }

    val solutions = ArrayList<IntArray>()

    var x: Int? = 1

    fun getActions(gameState: AbstractGameState, playerId: Int): IntArray {
        var solution = buffer ?: randomPoint(gameState.nActions())
        if (useShiftBuffer) {
            if (solution == null)
                solution = randomPoint(gameState.nActions())
            else {
                val numberToShiftLeft = if (gameState is ActionAbstractGameState) gameState.codonsPerAction() else 1
                solution = shiftLeftAndRandomAppend(solution, numberToShiftLeft, gameState.nActions())
            }
        } else {
            // System.out.println("New random solution with nActions = " + gameState.nActions())
            solution = randomPoint(gameState.nActions())
        }
        solutions.clear()
        solutions.add(solution)
        for (i in 0 until nEvals) {
            // evaluate the current one
     //       println("Evaluation $i for player $playerId")
            val mut = mutate(solution, probMutation, gameState.nActions())
            val curScore = evalSeq(gameState.copy(), solution, playerId)
            val mutScore = evalSeq(gameState.copy(), mut, playerId)
            if (mutScore >= curScore) {
                solution = mut
            }
            solutions.add(solution)
        }
        buffer = solution
        return solution
    }

    private fun mutate(v: IntArray, mutProb: Double, nActions: Int): IntArray {

        if (useMutationTransducer) {
            // build it dynamically in case any of the params have changed
            val mt = MutationTransducer(mutProb, repeatProb)
            return mt.mutate(v, nActions)
        }

        val n = v.size
        val x = IntArray(n)
        // pointwise probability of additional mutations
        // choose element of vector to mutate
        var ix = random.nextInt(n)
        if (!flipAtLeastOneValue) {
            // setting this to -1 means it will never match the first clause in the if statement in the loop
            // leaving it at the randomly chosen value ensures that at least one bit (or more generally value) is always flipped
            ix = -1
        }
        // copy all the values faithfully apart from the chosen one
        for (i in 0 until n) {
            if (i == ix || random.nextDouble() < mutProb) {
                x[i] = mutateValue(v[i], nActions)
            } else {
                x[i] = v[i]
            }
        }
        return x
    }

    private fun mutateValue(cur: Int, nPossible: Int): Int {
        // the range is nPossible-1, since we
        // selecting the current value is not allowed
        // therefore we add 1 if the randomly chosen
        // value is greater than or equal to the current value
        if (nPossible <= 1) return cur
        val rx = random.nextInt(nPossible - 1)
        return if (rx >= cur) rx + 1 else rx
    }

    private fun randomPoint(nValues: Int): IntArray {
        val p = IntArray(sequenceLength)
        for (i in p.indices) {
            p[i] = random.nextInt(nValues)
        }
        return p
    }

    private fun shiftLeftAndRandomAppend(v: IntArray, shift: Int, nActions: Int): IntArray {
        val p = IntArray(v.size)
        for (i in 0 until p.size - shift) {
            p[i] = v[i + shift]
        }
        for (i in shift..1)
            p[p.size - i] = random.nextInt(nActions)
        return p
    }


    private fun evalSeq(gameState: AbstractGameState, seq: IntArray, playerId: Int): Double {
        return if (discountFactor == null) {
            evalSeqNoDiscount(gameState, seq, playerId)
        } else {
            evalSeqDiscounted(gameState, seq, playerId, discountFactor!!)
        }
    }

    private fun evalSeqNoDiscount(gameState: AbstractGameState, seq: IntArray, playerId: Int): Double {
        var gameState = gameState
        val current = gameState.score()
        val intPerAction = if (gameState is ActionAbstractGameState) gameState.codonsPerAction() else 1
        val actions = IntArray(2 * intPerAction)
        var currentActionPointer = 0

        for (action in seq) {
            actions[playerId * intPerAction + currentActionPointer] = action
            //TODO: This is fine with an opponent model that does nothing...but will not work otherwise
            // The problem being that SimpleAgentInterface only permits getAction: Int
            actions[(1 - playerId) * intPerAction + currentActionPointer] = opponentModel.getAction(gameState, 1 - playerId)
            when (gameState) {
                is ActionAbstractGameState -> {
                    currentActionPointer++
                    if (currentActionPointer == intPerAction) {
                        val action1 = gameState.translateGene(0, actions.sliceArray(0..intPerAction))
                        val action2 = gameState.translateGene(1, actions.sliceArray(intPerAction..(2 * intPerAction - 1)))
       //                 println("\tBlue Action is $action1, Red Action is $action2")
                        gameState = gameState.next(listOf(action1, action2))
                        currentActionPointer = 0
                    }
                }
                else -> gameState = gameState.next(actions)
            }
        }

        val delta = gameState.score() - current
  //      println("Score change of evaluation is $delta")
        return if (playerId == 0)
            delta
        else
            -delta
    }

    private fun evalSeqDiscounted(gameState: AbstractGameState, seq: IntArray, playerId: Int, discountFactor: Double): Double {
        var gameState = gameState
        var currentScore = gameState.score()
        var delta = 0.0
        var discount = 1.0
        val actions = IntArray(2)
        for (action in seq) {
            actions[playerId] = action
            actions[1 - playerId] = opponentModel.getAction(gameState, 1 - playerId)
            gameState = gameState.next(actions)
            val nextScore = gameState.score()
            val tickDelta = nextScore - currentScore
            currentScore = nextScore
            delta += tickDelta * discount
            discount *= discountFactor
        }
        return if (playerId == 0)
            delta
        else
            -delta
    }

    override fun toString(): String {
        return "SEA: $nEvals : $sequenceLength : $opponentModel"
    }

    override fun getAction(gameState: AbstractGameState, playerId: Int): Int {
        return getActions(gameState, playerId)[0]
    }

    fun getSolutionsCopy(): ArrayList<IntArray> {

        val x = ArrayList<IntArray>()
        x.addAll(solutions)
        return x
    }
}
