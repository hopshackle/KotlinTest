package ggi

interface AbstractGameState {

    fun copy(): AbstractGameState

    // the ith entry of the actions array is the action for the ith player
    // next is used to advance the state of the game given the current
    // set of actions
    // this can either be for the 'real' game
    // or for a copy of the game to use in statistical forward planning, for example
    // fun next(actions: IntArray, playerId: Int): AbstractGameState
    fun next(actions: IntArray): AbstractGameState

    // the number of actions available to a player in the current state
    fun nActions(): Int

    fun score(): Double

    fun isTerminal(): Boolean

    fun nTicks(): Int

}

interface ExtendedAbstractGameState : AbstractGameState {
    fun totalTicks() : Long

    fun resetTotalTicks(): Unit

    fun randomInitialState(): AbstractGameState
}

interface ActionAbstractGameState : AbstractGameState {

    fun playerCount(): Int

    fun codonsPerAction(): Int

    override fun nActions(): Int {
        throw AssertionError("Should use codonsPerAction() to determine length of genome for Action")
    }
    fun possibleActions(player: Int): List<Action>

    fun next(actions: List<Action>) : ActionAbstractGameState
    // actions keyed by playerID

    override fun next(actions: IntArray) : AbstractGameState {
        throw AssertionError("Should use next() with Map of player -> Action")
    }

    fun translateGene(player: Int, gene: IntArray) : Action
}

interface Action {
    fun apply(state: ActionAbstractGameState) : ActionAbstractGameState
}

