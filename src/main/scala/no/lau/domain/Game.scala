package no.lau.domain

import collection.mutable.HashMap
import no.lau.domain.movement.{StackableMovement, Movable}

import scala.collection.jcl.ArrayList


/**
 * BoardSize X and Y start from 0 to make computation easier to write :)
 */
case class Game(boardSizeX: Int, boardSizeY: Int) {
  val rnd = new scala.util.Random

  var gameBoards:List[HashMap[Tuple2[Int, Int], GamePiece]] = List(new HashMap[Tuple2[Int, Int], GamePiece])

  def currentGameBoard():HashMap[Tuple2[Int, Int], GamePiece] = gameBoards.first
  def previousGameBoard():HashMap[Tuple2[Int, Int], GamePiece] = {
    if(gameBoards.tail.size > 0)
      gameBoards.tail.first
    else
      gameBoards.first
  }

  def newTurn = {
    gameBoards = cloneCurrent :: gameBoards
    for(gamePiece <- previousGameBoard.values) {
      gamePiece match {
        case stackable: StackableMovement => {
          stackable match {
            case movable: Movable => {
              if(stackable.movementStack.size > 0) println(stackable.movementStack)
              stackable.movementStack.firstOption match {
                case Some(direction) => {
                  try {
                    movable.move(direction)
                    stackable.movementStack = stackable.movementStack.tail
                  } catch {
                    case ime: IllegalMoveException => {
                      println("Illegal Move for " + movable + ": "+ ime.getMessage)
                      stackable.movementStack = List()
                      stackable.progressionHalted
                    }
                  }
                }
                case None =>
              }
            }
          }
        }
        case _ => 
      }
    }
    //For all stackable pieces - check if they have a movement scheduled
    //Check if the movement collides with other movements, in which case, roll back both movements
    //Remove a movement from the stack of the stackableMovement

    currentGameBoard
  }

  private def cloneCurrent = currentGameBoard.clone.asInstanceOf[HashMap[Tuple2[Int, Int], GamePiece]]

  /**
   * Simple algorithm for scattering out different objects.
   * 
   * Linear execution time to the number of cells in the board. 
   */
  def findRandomFreeCell(): Tuple2[Int, Int] = {
    val freeCells = new ArrayList[Tuple2[Int, Int]]()
    for (i <- 0 to boardSizeX){
      for (j <- 0 to boardSizeY) {
        val cell = (i, j)
        if (currentGameBoard.get(cell) isEmpty){
          freeCells += cell
        }
      }
    }
    freeCells(rnd.nextInt(freeCells.length))
  }

  def addRandomly(gamePiece: GamePiece) = currentGameBoard += findRandomFreeCell -> gamePiece

  //todo if two monsters wish to move to the same tile, a ArrayIndexOutOfBoundsException: -1 can be thrown! Needs to be fixed 
  def whereIs(gamePiece: GamePiece, gameBoard:HashMap[Tuple2[Int, Int], GamePiece]): Tuple2[Int, Int] = {
    val foundItAt: Int = gameBoard.values.indexOf(gamePiece)
    gameBoard.keySet.toArray(foundItAt)
  }

  def printableBoard = {
    val table = for (y <- 0 to boardSizeY)
    yield {
        val row = for (x <- 0 to boardSizeX)
        yield currentGameBoard.getOrElse((x, boardSizeY - y), ".")
        row.foldLeft("")(_ + _) + "\n"
      }
    table.foldLeft("")(_ + _)
  }
}

trait GamePiece

case class Monster(game: Game, id: Any) extends GamePiece{
  override def toString = "H"
}

//Todo blocks should need no identity. When one is moved, it is essentially deleted, and replaced by a new. If possible :) Or has an autogenerated id
case class Block(game: Game, id: Any) extends Movable {override def toString = "B"}

case class StaticWall() extends GamePiece {override def toString = "W"}

case class IllegalMoveException(val message: String) extends Throwable {
  override def getMessage = message
}

package movement {

trait StackableMovement extends Movable {
  var movementStack:List[Direction] = List()
  def stackMovement(dir:Direction) { movementStack = movementStack ::: List(dir) }
  def progressionHalted { println("Further progression halted") } //todo implement what clients should do when progression halts
}

trait Movable extends GamePiece {
  val game: Game //todo game should preferably be referenced some other way

  /**
   * Used for moving gamepieces around the gameBoard
   * If the route ends up in an illegal move at one stage, the movement will be dropped and an IllegalMovementException will be thrown
   * todo should probably return new location
   **/

  def move(inThatDirection: Direction) {
    val oldLocation = game.whereIs(this, game.previousGameBoard)
    val newLocation = tryToMove(inThatDirection)
    move(oldLocation, newLocation)
  }


  def tryToMove(inThatDirection: Direction):Tuple2[Int, Int] = {
    val oldLocation = game.whereIs(this, game.previousGameBoard)

    val newLocation = (oldLocation._1 + inThatDirection.dir._1, oldLocation._2 + inThatDirection.dir._2)

    if (isOverBorder(newLocation))
      throw IllegalMoveException("Move caused movable to travel across the border")

    //Is this the correct way to do this?
    whosInMyWay(newLocation) match {
      case mortal: Mortal => {
        this match {
          case meelee:Meelee => mortal.kill
        }
        //todo Not sure what should be done for squeezing
        /*
        val wasSqueezed = try {
          mortal match {
            case movable:Movable => movable.tryToMove(inThatDirection); false}
          }
        catch {
          case ime: IllegalMoveException => mortal kill; true
        }
        if(!wasSqueezed) throw IllegalMoveException("Nothing to be squeezed against")
        */
      }
      case movable: Movable => movable.move(inThatDirection)
      case gamePiece: GamePiece => throw IllegalMoveException("Trying to move unmovable Gamepiece")
      case None =>
    }
    newLocation
  }

  private def whosInMyWay(newLocation:Tuple2[Int, Int]) = game.previousGameBoard.getOrElse(newLocation, None)

  private def isOverBorder(newLocation: Tuple2[Int, Int]) = newLocation._1 > game.boardSizeX || newLocation._1 < 0 || newLocation._2 > game.boardSizeY || newLocation._2 < 0

  private def move(oldLocation: Tuple2[Int, Int], newLocation: Tuple2[Int, Int]) {
    game.currentGameBoard -= oldLocation
    game.currentGameBoard += newLocation -> this
  }

  def whereAreYou = game.whereIs(this, game.previousGameBoard)
}

/**
 * Marks that a gamePiece can be killed
 */
trait Mortal {
  var isKilled = false
  def kill() {isKilled = true; println("I was killed!")}
}
// Marks that a Monster kan kill by eating
trait Meelee

// Direction enum should preferably also provide a matrix to indicate that Up is (+1, +0), which could mean that Move didn't have to include the pattern matching.
object Direction extends Enumeration {
  val Up, Down, Right, Left = Value
}

sealed abstract class Direction(val dir: Tuple2[Int, Int])
case object Up extends Direction(0, 1)
case object Down extends Direction(0, -1)
case object Right extends Direction(1, 0)
case object Left extends Direction(-1, 0)
}
