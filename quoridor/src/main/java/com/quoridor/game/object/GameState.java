package com.quoridor.game.object;

import java.awt.Point;
import java.util.Vector;

import com.quoridor.agents.Player;
import com.quoridor.enums.ACTION_MODE;
import com.quoridor.enums.GAME_MODE;
import com.quoridor.enums.GAME_OPTION;
import com.quoridor.enums.PLAYER_COLOR;
import com.quoridor.game.manager.OptionManager;
import com.quoridor.logger.MyLogger;
import com.quoridor.utils.Utils;


public class GameState{
	private OptionManager optionManager = OptionManager.getInstance();

	private GAME_MODE gameMode; // 첫 화면에서 선택한 게임 모드

    // 플레이어
	private Player blackPlayer;
	private Player whitePlayer;
	private Player currentPlayer; // 현재 플레이어
	private Player opponentPlayer;

    private PLAYER_COLOR startColor = PLAYER_COLOR.BLACK;
	private ACTION_MODE actionMode = ACTION_MODE.MOVE_MODE; // 장애물 버튼을 눌렀는지에 대한 변수
	private int currentTurnCount = 0;
	private Vector<Point> availableMoves = new Vector<Point>();
	private int numOfRepetitionsToMoveTurn = 2;
	
	public boolean giveUp = false;
	public boolean gameOver = false;
	private String winner = null;

	private int volume = Integer.parseInt(optionManager.getConfig(GAME_OPTION.VOLUME));
	private GameSound moveSound = new GameSound("quoridor\\src\\main\\java\\resources\\QuoridorResources\\sounds\\move.wav", volume);	// 이동 사운드
	private GameSound wallSound = new GameSound("quoridor\\src\\main\\java\\resources\\QuoridorResources\\sounds\\wall.wav", volume);	// 벽 사운드

    // 벽
	private boolean[][][] wallPoints = new boolean[2][8][8];
	private int[][][] availableWalls = new int[2][8][8];
	// [0][][] -> 가로벽
	// [1][][] -> 세로벽
	
	private Vector<TurnInfo> actionHistory = new Vector<TurnInfo>();

    public GameState(GAME_MODE gameMode, Player blackPlayer, Player whitePlayer, PLAYER_COLOR startColor) {
		// Play 용도
		assert (gameMode != GAME_MODE.REPLAY);
		this.gameMode = gameMode;
		this.blackPlayer = blackPlayer;
		this.whitePlayer = whitePlayer;
		MyLogger.getInstance().info("게임이 실행되었습니다. 게임 모드: " + gameMode);

		this.startColor = startColor;

		if(gameMode == GAME_MODE.REPLAY) {
			numOfRepetitionsToMoveTurn = 1;
		}

		initialize();
	}

    public boolean[][][] getWalls() { return wallPoints; }
	public int[][][] getAvailableWalls() { return availableWalls; }
	public Vector<Point> getAvailableMoves() { return availableMoves; }
	public PLAYER_COLOR getStartColor() { return startColor; }
	public Player getBlackPlayer() { return blackPlayer; }
	public Player getWhitePlayer() { return whitePlayer; }
	public Player getCurrentPlayer() { return currentPlayer; }
	public Player getOpponentPlayer() { return opponentPlayer; }
	public GAME_MODE getGameMode() { return gameMode; }
	public ACTION_MODE getActionMode() { return actionMode; }
	public int getCurrentTurnCount() { return currentTurnCount; }
	public int getTurnCountSize() { return actionHistory.size(); }
	public void ChangeActionMode() { actionMode = actionMode.changeMode(); }
	public Vector<TurnInfo> getHistory() { return actionHistory; }
	public boolean isGiveUp() { return giveUp; }
	public boolean isGameOver() { return gameOver; }

	public String getWinner() {
		assert(winner != null);
		return winner;
	}

	// 게임 기록 받아오기
	public void setHistory(Vector<TurnInfo> history) {
		assert (actionHistory.size() == 0);
		this.actionHistory = history;
	}

	public void initialize() {
		initializePlayerPoint();
		initializeCurrentPlayer();
		initializeWalls();
		updateAvailableMoves();
	}

	// 플레이어 위치 초기화
	public void initializePlayerPoint() {
		blackPlayer.move(new Point(4, 8));
		whitePlayer.move(new Point(4, 0));
	}

	// 벽 초기화
	public void initializeWalls() {
		for(int k=0; k < 2; k++) {
			for(int i=0; i < 8; i++) {
				for(int j=0; j < 8; j++) {
					wallPoints[k][i][j] = false;
					availableWalls[k][i][j] = 0;
				}
			}
		}
	}

	// 현재 플레이어 초기 설정
	public void initializeCurrentPlayer() {
		if(startColor == PLAYER_COLOR.BLACK) {
			currentPlayer = blackPlayer;
			opponentPlayer = whitePlayer;
		} else {
			currentPlayer = whitePlayer;
			opponentPlayer = blackPlayer;
		}
	}

	// 한 턴의 프로세스를 모두 실행하는 함수
	public void proceedTurnAction(TurnInfo turnInfo) {
		// History를 업데이트
		// History에 현재 TurnInfo 추가
		// action 수행
		// Next Turn
		// 게임 오버 체크
		GameAction action = turnInfo.getAction();
		Utils.logAction(currentPlayer.getPlayerColor(), action);
		updateHistory();
		addHistory(new TurnInfo(currentPlayer.getPlayerColor(), currentPlayer.getPoint(), action));
		applyAction(action);
		nextTurn();
		checkGameOver();
	}

    // 턴을 바꾸는 함수
	public void changeTurn() {
		currentPlayer = opponentPlayer;
		opponentPlayer = (currentPlayer == blackPlayer) ? whitePlayer : blackPlayer;
	}

	// 행동을 한 다음 그 이후 차례로 저장된 행동 제거
	public void updateHistory() {
        // 현재 턴 이후 기록 제거
        // 되돌리기를 한 다음 돌을 놓을 경우를 위해서 필요
		int historySize = actionHistory.size();
		for(int i = currentTurnCount; i < historySize; i++) {
			actionHistory.remove(currentTurnCount);
		}
	}

	// 턴 행동 저장
	public void addHistory(TurnInfo turnInfo) {
		actionHistory.add(turnInfo);
	}

	// 다음 턴
	public void nextTurn() {
		changeTurn();
		currentTurnCount++;
		actionMode = ACTION_MODE.MOVE_MODE;
		updateAvailableMoves();
	}

	public TurnInfo getCurrentTurnInfo() {
		return actionHistory.get(currentTurnCount);
	}

	// 저장된 이전 턴 행동이 있는지 확인
	public boolean checkCanMoveToPrev() {
		if(gameMode == GAME_MODE.REPLAY) {
			if(currentTurnCount > 0) {
				return true;
			} else {
				return false;
			}
		} else {
			if(currentTurnCount < 2) {
				return false;
			} else {
				return true;
			}
		}
	}

	// 저장된 다음 턴 행동이 있는지 확인
	public boolean checkCanMoveToNext() {
		if(currentTurnCount < getTurnCountSize()) {
			return true;
		} else {
			return false;
		}
	}

	// 저장된 이전 턴 행동 실행
	public void moveToPrevTurn() {
		for(int i=0; i < numOfRepetitionsToMoveTurn; i++) {
			changeTurn();
			currentTurnCount--;
			TurnInfo prevTurnInfo = getCurrentTurnInfo();
			GameAction prevAction = prevTurnInfo.getAction();
			if(prevAction.getActionMode().isWallMode()) {
				deleteWall(prevAction);
			} else {
				applyAction(new GameAction(prevTurnInfo.getStartPoint()));
			}
		}
		updateAvailableMoves();
	}

	// 저장된 다음 턴 행동 실행
	public void moveToNextTurn() {
		for(int i=0; i < numOfRepetitionsToMoveTurn; i++) {
			TurnInfo nextTurnInfo = getCurrentTurnInfo();
			GameAction nextAction = nextTurnInfo.getAction();
			applyAction(nextAction);
			changeTurn();
			currentTurnCount++;
		}
		updateAvailableMoves();
	}

	// 저장된 턴 행동 모두 실행
	public void moveToLastTurn() {
		for(int i=0; i < getTurnCountSize(); i++) {
			TurnInfo nextTurnInfo = getCurrentTurnInfo();
			GameAction nextAction = nextTurnInfo.getAction();
			applyAction(nextAction);
			changeTurn();
			currentTurnCount++;
		}
		updateAvailableMoves();
	}

	// 행동을 게임에 적용
    public void applyAction(GameAction action) {
		if(action.getActionMode().isWallMode()) {
			putWall(action);
			wallSound.play();
		} else {
			currentPlayer.move(action.getPoint());
			moveSound.play();
		}
	}

    // 벽을 놓는 함수
	public void putWall(GameAction action) {
		boolean vertical = action.isVertical();
		int yPos = action.getPoint().y;
		int xPos = action.getPoint().x;

		currentPlayer.decreaseNumWalls();

		if(vertical) {
			wallPoints[1][yPos][xPos] = true;
			// 불가능해진 벽 위치 찾기
			availableWalls[1][yPos][xPos]++;
			availableWalls[0][yPos][xPos]++;

			if(yPos < 7) {
				availableWalls[1][(yPos + 1)][xPos]++;
			}
			if(yPos > 0) {
				availableWalls[1][(yPos - 1)][xPos]++;
			}
			
		} else {
			wallPoints[0][yPos][xPos] = true;
			// 불가능해진 벽 위치 찾기
			availableWalls[0][yPos][xPos]++;
			availableWalls[1][yPos][xPos]++;

			if(xPos < 7) {
				availableWalls[0][yPos][(xPos + 1)]++;
			}
			if(xPos > 0) {
				availableWalls[0][yPos][(xPos - 1)]++;
			}
		}
	}
	
	// 벽을 제거하는 함수
	public void deleteWall(GameAction action) {
		boolean vertical = action.isVertical();
		int yPos = action.getPoint().y;
		int xPos = action.getPoint().x;
		
		if(vertical) {
			wallPoints[1][yPos][xPos] = false;
			// 가능해진 벽 위치 찾기
			availableWalls[1][yPos][xPos]--;
			availableWalls[0][yPos][xPos]--;
			if(yPos < 7) {
				availableWalls[1][(yPos + 1)][xPos]--;
			}
			if(yPos > 0) {
				availableWalls[1][(yPos - 1)][xPos]--;
			}

			currentPlayer.increaseNumWalls();	// 놓을 수 있는 벽의 수 증가
		} else {
			wallPoints[0][yPos][xPos] = false;

			availableWalls[0][yPos][xPos]--;
			availableWalls[1][yPos][xPos]--;

			if(xPos < 7) {
				availableWalls[0][yPos][(xPos + 1)]--;
			}
			if(xPos > 0) {
				availableWalls[0][yPos][(xPos - 1)]--;
			}
			currentPlayer.increaseNumWalls();	// 놓을 수 있는 벽의 수 증가
		}
	}

	// 게임 오버인지 검사
	public void checkGameOver() { 
		if(!gameOver) {
			if(blackPlayer.getPoint().y == 0) {
				winner = blackPlayer.getPlayerName();
				gameOver = true;
			} else if(whitePlayer.getPoint().y == 8) {
				winner = whitePlayer.getPlayerName();
				gameOver = true;
			}
		}
	}

	// 현재 플레이어가 항복하는 함수
	public void giveUp() {
		winner = opponentPlayer.getPlayerName();
		gameOver = true;
		giveUp = true;
	}

	// 가능한 벽 놓기 행동인지 검사
	public boolean checkAvailableWall(GameAction action) {
		int yPos = action.getPoint().y;
		int xPos = action.getPoint().x;

		if(action.isVertical()) {
			return availableWalls[1][yPos][xPos] == 0;
		} else {
			return availableWalls[0][yPos][xPos] == 0;
		}
	}

	// 가능한 행동인지 검사
	public boolean checkAvailableAction(GameAction action) {
		if(action.getActionMode() == ACTION_MODE.WALL_MODE) {
			return checkAvailableWall(action);
		} else {
			boolean available = false;
			for(int i=0; i < availableMoves.size(); i++) {
				if(availableMoves.get(i).y == action.getPoint().y &&
				availableMoves.get(i).x == action.getPoint().x) {
					available = true;
					break;
				}
			}
			return available;
		}
	}

	// 가능한 이동 목록 업데이트
	public void updateAvailableMoves() {
		availableMoves.clear();
		Point currentPlayerPoint = currentPlayer.getPoint();
		Point opponentPoint = opponentPlayer.getPoint();

		// 플레이어 위치가 0일때와 8일때 벽을 계산하기 쉽게 하기 위한 변수
		int minWallYPoint = 0;
		int maxWallYPoint = 8;
		int minWallXPoint = 0;
		int maxWallXPoint = 8;
		
		if(currentPlayerPoint.y <= 0) minWallYPoint = 0; else minWallYPoint = currentPlayerPoint.y - 1;
		if(currentPlayerPoint.y >= 8) maxWallYPoint = 7; else maxWallYPoint = currentPlayerPoint.y;
		if(currentPlayerPoint.x <= 0) minWallXPoint = 0; else minWallXPoint = currentPlayerPoint.x - 1;
		if(currentPlayerPoint.x >= 8) maxWallXPoint = 7; else maxWallXPoint = currentPlayerPoint.x;
		
		// 오른쪽 이동 가능 여부
		if(currentPlayerPoint.x < 8) { 
			if(wallPoints[1][minWallYPoint][currentPlayerPoint.x] == false && wallPoints[1][maxWallYPoint][currentPlayerPoint.x] == false) { // 오른쪽에 벽이 없을 때
				if(opponentPoint.x == currentPlayerPoint.x + 1 && opponentPoint.y == currentPlayerPoint.y && currentPlayerPoint.x < 8) { // 상대랑 붙어 있으면서 건너 뛸 수 있는 상황일 때
					if(wallPoints[1][minWallYPoint][(currentPlayerPoint.x + 1 > 7)? 7 : currentPlayerPoint.x + 1] == false &&
					wallPoints[1][maxWallYPoint][(currentPlayerPoint.x + 1 > 7)? 7 : currentPlayerPoint.x + 1] == false) {
						if(currentPlayerPoint.x + 2 <= 8) {	// 뛰어넘었을 때 맵을 벗어나지 않는 경우
							availableMoves.add(new Point(currentPlayerPoint.x + 2, currentPlayerPoint.y));
						}
					}
				} else {
					availableMoves.add(new Point(currentPlayerPoint.x + 1, currentPlayerPoint.y));
				}
			}
		}
		
		// 왼쪽 이동 가능 여부
		if(currentPlayerPoint.x > 0) {
			if(wallPoints[1][minWallYPoint][currentPlayerPoint.x - 1] == false && wallPoints[1][maxWallYPoint][currentPlayerPoint.x - 1] == false) {
				if(opponentPoint.x == currentPlayerPoint.x - 1 && opponentPoint.y == currentPlayerPoint.y && currentPlayerPoint.x > 0) {
					if(wallPoints[1][minWallYPoint][(currentPlayerPoint.x - 2 < 0 )? 0 : currentPlayerPoint.x - 2] == false &&
					wallPoints[1][maxWallYPoint][(currentPlayerPoint.x - 2 < 0)? 0 : currentPlayerPoint.x - 2] == false) {
						if(currentPlayerPoint.x - 2 >= 0) {
							availableMoves.add(new Point(currentPlayerPoint.x - 2, currentPlayerPoint.y));
						}
					}
				} else {
					availableMoves.add(new Point(currentPlayerPoint.x - 1, currentPlayerPoint.y));
				}
			}
		}
		
		// 아래쪽 이동 가능 여부
		if(currentPlayerPoint.y < 8) {
			if(wallPoints[0][currentPlayerPoint.y][minWallXPoint] == false && wallPoints[0][currentPlayerPoint.y][maxWallXPoint] == false) {
				if(opponentPoint.x == currentPlayerPoint.x && opponentPoint.y == currentPlayerPoint.y + 1 && currentPlayerPoint.y < 8) {
					if(wallPoints[0][(currentPlayerPoint.y + 1 > 7)? 7 : currentPlayerPoint.y + 1][minWallXPoint] == false &&
					wallPoints[0][(currentPlayerPoint.y + 1 > 7)? 7 : currentPlayerPoint.y + 1][maxWallXPoint] == false) {
						if(currentPlayerPoint.y + 2 <= 8) {
							availableMoves.add(new Point(currentPlayerPoint.x, currentPlayerPoint.y + 2));
						}
					}
				} else {
					availableMoves.add(new Point(currentPlayerPoint.x, currentPlayerPoint.y + 1));
				}
			}
		}
		
		// 위쪽 이동 가능 여부
		if(currentPlayerPoint.y > 0) {
			if(wallPoints[0][currentPlayerPoint.y - 1][minWallXPoint] == false && wallPoints[0][currentPlayerPoint.y - 1][maxWallXPoint] == false) {
				if(opponentPoint.x == currentPlayerPoint.x && opponentPoint.y == currentPlayerPoint.y - 1 && currentPlayerPoint.y > 0) {
					if(wallPoints[0][(currentPlayerPoint.y - 2 < 0)? 0 : currentPlayerPoint.y - 2][minWallXPoint] == false &&
					wallPoints[0][(currentPlayerPoint.y - 2 < 0)? 0 : currentPlayerPoint.y - 2][maxWallXPoint] == false) {
						if(currentPlayerPoint.y - 2 >= 0) {
							availableMoves.add(new Point(currentPlayerPoint.x, currentPlayerPoint.y - 2));
						}
					}
				} else {
					availableMoves.add(new Point(currentPlayerPoint.x, currentPlayerPoint.y - 1));
				}
			}
		}
	}
}