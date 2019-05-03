package games.eventqueuegame

import games.simplegridgame.w
import ggi.AbstractGameState
import ggi.Action
import ggi.ActionAbstractGameState
import ggi.ExtendedAbstractGameState
import math.Vec2d
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.nextUp
import kotlin.math.roundToInt
import kotlin.random.Random

// todo : Create and test and Event Queue

data class Event(val tick: Int, val action: Action) : Comparable<Event> {
    override fun compareTo(other: Event): Int {
        return tick.compareTo(other.tick)
    }
}

/*
 A returned positive value is the number of surviving attackers; a negative value is interpreted as the number of
 surviving defenders, with the attack repulsed
 */
fun lanchesterLinearBattle(attack: Double, defence: Double, attackerDamageCoeff: Double, defenderDamageCoeff: Double): Double {
    var attackingForce = attack
    var defendingForce = defence
    var count = 0
    do {
        val attackDmg = attackingForce * attackerDamageCoeff
        val defenceDmg = defendingForce * defenderDamageCoeff
        attackingForce -= defenceDmg
        defendingForce -= attackDmg
        count++
    } while (attackingForce > 0.0 && defendingForce > 0.0 && count < 100)
    return if (defendingForce > 0.0) -defendingForce else attackingForce
}

enum class PlayerId {
    Blue, Red, Neutral, Fog
}

data class City(val location: Vec2d, val radius: Int = 10, var pop: Int = 100, var owner: PlayerId = PlayerId.Neutral)

data class EventGameParams(
        val nAttempts: Int = 10,
        val width: Int = 1000,
        val height: Int = 600,
        //      var minRad: Int = 20,
        //      var maxRad: Int = 100,
        //      var minPop: Int = 10,
        val maxPop: Int = 100,
        val minSep: Int = 30
)


data class World(var cities: ArrayList<City> = ArrayList(), val width: Int = 1000, val height: Int = 600,
                 val speed: Double = 1.0,
                 val random: Random = Random(3),
                 val params: EventGameParams = EventGameParams()) {

    init {
        initialise()
    }

    var currentTransits: ArrayList<Transit> = ArrayList()
    var currentTicks: Int = 0

    private fun initialise() {
        // just keep it like so
        cities = ArrayList()
        with(params) {
            for (i in 0 until nAttempts) {
                val location = Vec2d(minSep + random.nextDouble((width - 2.0 * minSep)),
                        minSep + random.nextDouble((height - 2.0 * minSep)))
                val city = City(location, minSep / 2, 0)
                if (canPlace(city, cities, minSep)) cities.add(city)
            }
        }
        var blueBase = 0
        var redBase = 0
        while (blueBase == redBase) {
            blueBase = random.nextInt(cities.size)
            redBase = random.nextInt(cities.size)
        }
        cities[blueBase].owner = PlayerId.Blue
        cities[blueBase].pop = params.maxPop
        cities[redBase].owner = PlayerId.Red
        cities[redBase].pop = params.maxPop
    }

    fun setTime(time: Int) {
        currentTicks = time
    }

    fun canPlace(c: City, cities: List<City>, minSep: Int): Boolean {
        for (el in cities)
            if (c.location.distanceTo(el.location) < c.radius + el.radius + minSep) return false
        return true
    }

    fun fogTest(id: PlayerId): World {
        cities.forEach { c ->
            if (c.owner != id) {
                // fog it out
                c.owner = PlayerId.Fog
                c.pop = -1
            }
        }
        return this
    }

    fun deepCopy(): World {
        val state = copy()
        state.cities = ArrayList(cities.map { c -> City(c.location, c.radius, c.pop, c.owner) })
        state.currentTransits = ArrayList(currentTransits.filter { true })
        state.currentTicks = currentTicks
        return state
    }

    fun addTransit(transit: Transit) {
        currentTransits.add(transit)
    }

    fun removeTransit(transit: Transit) {
        if (currentTransits.contains(transit))
            currentTransits.remove(transit)
        else
            throw AssertionError("Transit to be removed is not recognised")
    }

}

data class Transit(val nPeople: Int, val fromCity: Int, val toCity: Int, val playerId: PlayerId, val startTime: Int, val endTime: Int)

data class CityEfflux(val transit: Transit) : Action {
    override fun apply(state: ActionAbstractGameState): ActionAbstractGameState {
        if (state is EventQueueGame) {
            val world = state.world
            val city = world.cities[transit.fromCity]
            if (city.owner == transit.playerId) {
                if (city.pop < transit.nPeople)
                    throw AssertionError("Invalid Transit - maximum force move is limited to city population")
                city.pop -= transit.nPeople
            } else {
                throw AssertionError("Invalid Transit - must be from city owned by playerId")
            }
            world.addTransit(transit)
        }
        return state
    }
}

data class CityInflux(val transit: Transit) : Action {
    override fun apply(state: ActionAbstractGameState): ActionAbstractGameState {
        if (state is EventQueueGame) {
            val world = state.world
            val city = world.cities[transit.toCity]
            if (city.owner == transit.playerId) {
                city.pop += transit.nPeople
            } else {
                val result = lanchesterLinearBattle(transit.nPeople.toDouble(), city.pop.toDouble(), 0.05, 0.05)
                if (result > 0.0) {
                    // attackers win
                    city.owner = transit.playerId
                    city.pop = result.toInt()
                } else {
                    // defenders win
                    city.pop = -result.toInt()
                }
            }
            world.removeTransit(transit)
        }
        return state
    }
}

data class LaunchExpedition(val player: PlayerId, val from: Int, val to: Int, val proportion: Int, val wait: Int) : Action {
    override fun apply(state: ActionAbstractGameState): ActionAbstractGameState {
        if (state is EventQueueGame) {
            val world = state.world
            if (isValid(world)) {
                val sourceCityPop = world.cities[from].pop
                val maxActions = world.cities.size.toDouble()
                val distance = world.cities[from].location.distanceTo(world.cities[to].location)
                val arrivalTime = world.currentTicks + (distance / world.speed).toInt()
                val transit = Transit((proportion / maxActions * sourceCityPop).toInt() + 1, from, to, player,
                        world.currentTicks, arrivalTime)
                // we execute the troop departure immediately
                CityEfflux(transit).apply(state)
                // and put their arrival in the queue for the game state
                state.eventQueue.add(Event(arrivalTime, CityInflux(transit)))
            }
        }
        return state
    }

    fun isValid(world: World): Boolean {
        return world.cities[from].owner == player &&
                world.cities[from].pop > 0 &&
                from != to
    }
}

var totalTicks: Long = 0

class EventQueueGame : ActionAbstractGameState {

    var world = World()

    val eventQueue = PriorityQueue<Event>()

    override fun copy(): AbstractGameState {
        val state = EventQueueGame()
        state.world = world.deepCopy()
        state.eventQueue.addAll(eventQueue)
        return state
    }

    override fun playerCount() = 2

    override fun codonsPerAction() = 4

    override fun possibleActions(player: Int): List<Action> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun translateGene(player: Int, gene: IntArray): Action {
        val playerId: PlayerId = if (player == 0) PlayerId.Blue else PlayerId.Red
        return LaunchExpedition(playerId, gene.get(0), gene.get(1), gene.get(2), gene.get(3))
    }

    override fun next(actions: List<Action>): EventQueueGame {
        totalTicks++
        val currentTime = world.currentTicks
        world.setTime(currentTime + 1)

        var finished = false
        do {
            // we may have multiple events triggering in the same tick
            val event = eventQueue.peek()
            if (event != null && event.tick < currentTime) {
                // the time has come to trigger it
                eventQueue.poll()
                event.action.apply(this)
                //           println("Triggered event: ${event} in Game $this")
            } else {
                finished = true
            }
        } while (!finished)

/*        // we then take the new actions
        val blueAction = CityWarAction(PlayerId.Blue, actions[0], actions[1], actions[2], actions[3])
        val redAction = CityWarAction(PlayerId.Red, actions[4], actions[5], actions[6], actions[7])

        blueAction.apply(this)
        redAction.apply(this)
        */

        actions.forEach({ a -> a.apply(this) })

        return this
    }

    override fun nActions(): Int {
        return world.cities.size
    }

    override fun score(): Double {
        // we count the number of Blue cities, and subtract the number of red cities
        val blueCities = world.cities.count { c -> c.owner == PlayerId.Blue }
        val redCities = world.cities.count { c -> c.owner == PlayerId.Red }
        return (blueCities - redCities).toDouble()
    }

    override fun isTerminal(): Boolean {
        // game is over if all cities are controlled by the same player, whoever that is
        val player0 = world.cities[0].owner
        return world.cities.all { c -> c.owner == player0 }
    }

    override fun nTicks(): Int {
        return world.currentTicks
    }
/*
    override fun totalTicks(): Long {
        return totalTicks
    }

    override fun resetTotalTicks() {
        totalTicks = 0
    }

    override fun randomInitialState(): AbstractGameState {
        eventQueue.clear()
        world = World().randomize()
        return this
    }

    override fun intPerAction(): Int {
        return 4
    }
*/
}
