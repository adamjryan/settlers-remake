/*******************************************************************************
 * Copyright (c) 2015
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package jsettlers.graphics.map.minimap;

import java.util.Arrays;
import java.util.List;

import jsettlers.common.Color;
import jsettlers.common.CommonConstants;
import jsettlers.common.landscape.ELandscapeType;
import jsettlers.common.map.IGraphicsGrid;
import jsettlers.common.mapobject.EMapObjectType;
import jsettlers.common.mapobject.IMapObject;
import jsettlers.common.movable.EMovableType;
import jsettlers.common.movable.IMovable;
import jsettlers.graphics.map.MapDrawContext;
import jsettlers.graphics.map.minimap.MinimapMode.OccupiedAreaMode;
import jsettlers.graphics.map.minimap.MinimapMode.SettlersMode;

class LineLoader implements Runnable {
	protected static final short BLACK = 0x0001;
	private static final short TRANSPARENT = 0;
	private static final int Y_STEP_HEIGHT = 5;
	private static final int X_STEP_WIDTH = 5;
	private static final int LINES_PER_RUN = 30;

	private static final List<EMovableType> soildertypes = Arrays.asList(
			EMovableType.SWORDSMAN_L1,
			EMovableType.SWORDSMAN_L2,
			EMovableType.SWORDSMAN_L3,
			EMovableType.PIKEMAN_L1,
			EMovableType.PIKEMAN_L2,
			EMovableType.PIKEMAN_L3,
			EMovableType.BOWMAN_L1,
			EMovableType.BOWMAN_L2,
			EMovableType.BOWMAN_L3,
			EMovableType.GEOLOGIST,
			EMovableType.PIONEER
			);

	/**
	 * The minimap we work for.
	 */
	private final Minimap minimap;
	private boolean showBuildings = false;
	private int currentline = 0;
	private boolean stopped;

	private final MinimapMode modeSettings;

	private short[][] buffer = new short[1][1];
	private int currYOffset = 0;
	private int currXOffset = 0;

	public LineLoader(Minimap minimap, MinimapMode modeSettings) {
		this.minimap = minimap;
		this.modeSettings = modeSettings;
	}

	@Override
	public void run() {
		while (!stopped) {
			try {
				updateLine();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	};

	public void setShowBuildings(boolean showBuildings)
	{
		this.showBuildings = showBuildings;
		throw new UnsupportedOperationException("Not yet implemented");
	}

	private boolean isFirstRun;

	/**
	 * Updates a line by putting it to the update buffer. Next time the gl context is available, it is updated.
	 */
	private void updateLine() {
		minimap.blockUntilUpdateAllowedOrStopped();
		for (int i = 0; i < LINES_PER_RUN; i++) {

			if (buffer.length != minimap.getHeight()
					|| buffer[currentline].length != minimap.getWidth()) {
				buffer = new short[minimap.getHeight()][minimap.getWidth()];
				for (int y = 0; y < minimap.getHeight(); y++) {
					for (int x = 0; x < minimap.getWidth(); x++) {
						buffer[y][x] = BLACK;
					}
				}
				minimap.setBufferArray(buffer);
				currentline = 0;
				isFirstRun = true;
				currXOffset = 0;
				currYOffset = 0;
			}

			calculateLineData(currentline);
			minimap.setUpdatedLine(currentline);

			currentline += Y_STEP_HEIGHT;
			if (currentline >= minimap.getHeight()) {
				currYOffset++;
				if (currYOffset > Y_STEP_HEIGHT) {
					currYOffset = 0;
					currXOffset += 3;
					currXOffset %= X_STEP_WIDTH;
					if (currXOffset == 0) {
						isFirstRun = false;
					}
				}

				currentline = currYOffset;
			}
		}
	}

	private void calculateLineData(final int currentline) {
		// may change!
		final int safeWidth = this.minimap.getWidth();
		final int safeHeight = this.minimap.getHeight();
		final MapDrawContext context = this.minimap.getContext();
		final IGraphicsGrid map = context.getMap();

		// for height shades
		final short mapWidth = map.getWidth();
		final short mapHeight = map.getHeight();

		int mapLineHeight = mapHeight / safeHeight + 1;

		// first map tile in line
		int mapMaxY =
				(int) ((1 - (float) currentline / safeHeight) * mapHeight);
		// first map line not in line
		int mapMinY =
				(int) ((1 - (float) (currentline + 1) / safeHeight) * mapHeight);
		if (mapMinY == mapMaxY) {
			if (mapMaxY == mapHeight) {
				mapMinY = mapHeight - 1;
			} else {
				mapMaxY = mapMinY - 1;
			}
		}

		int myXOffset = (currXOffset + currentline * 3) % X_STEP_WIDTH;

		for (int x = myXOffset; x < safeWidth; x += X_STEP_WIDTH) {
			int mapMinX = (int) ((float) x / safeWidth * mapWidth);
			int mapMaxX = (int) ((float) (x + 1) / safeWidth * mapWidth);

			if (mapMinX != 0 && mapMaxX == mapMinX) {
				mapMinX = mapMaxX - 1;
			}
			int centerX = (mapMaxX + mapMinX) / 2;
			int centerY = (mapMaxY + mapMinY) / 2;

			short color = TRANSPARENT;
			byte visibleStatus = map.getVisibleStatus(centerX, centerY);
			if (visibleStatus > CommonConstants.FOG_OF_WAR_EXPLORED) {
				color =
						getSettlerForArea(map, context, mapMinX, mapMinY,
								mapMaxX, mapMaxY);
			}

			if (color == TRANSPARENT && (visibleStatus > CommonConstants.FOG_OF_WAR_EXPLORED || isFirstRun)) {
				float basecolor =
						((float) visibleStatus)
								/ CommonConstants.FOG_OF_WAR_VISIBLE;
				int dheight =
						map.getHeightAt(centerX, mapMinY)
								- map.getHeightAt(centerX, Math.min(mapMinY
										+ mapLineHeight, mapHeight - 1));
				basecolor *= (1 + .15f * dheight);

				if (basecolor >= 0) {
					color = getLandscapeForArea(map, context, mapMinX, mapMinY, mapMaxX, mapMaxY).toShortColor(basecolor);
				}
			}

			if (color != TRANSPARENT) {
				buffer[currentline][x] = color;
			}

		}

	}

	private Color getLandscapeForArea(IGraphicsGrid map, MapDrawContext context, int mapminx, int mapminy, int mapmaxx, int mapmaxy) {
		int centerx = (mapmaxx + mapminx) / 2;
		int centery = (mapmaxy + mapminy) / 2;

		ELandscapeType landscapeType = map.getLandscapeTypeAt(centerx, centery);

		if (modeSettings.simplifyLandscape()) {
			landscapeType = getSimplifiedLandscapeType(landscapeType);
		}
		return landscapeType.color;
	}

	private short getSettlerForArea(
			IGraphicsGrid map, MapDrawContext context, int mapminx, int mapminy, int mapmaxx,
			int mapmaxy) {

		SettlersMode displaySettlers = this.modeSettings.getDisplaySettlers();
		OccupiedAreaMode displayOccupied = this.modeSettings.getDisplayOccupied();
		boolean displayBuildings = this.modeSettings.getDisplayBuildings();

		short occupiedColor = TRANSPARENT;
		short settlerColor = TRANSPARENT;
		short buildingColor = TRANSPARENT;

		for (int y = mapminy; y < mapmaxy && (displayOccupied != OccupiedAreaMode.NONE || displayBuildings || displaySettlers != SettlersMode.NONE); y++) {
			for (int x = mapminx; x < mapmaxx
					&& (displayOccupied != OccupiedAreaMode.NONE || displayBuildings || displaySettlers != SettlersMode.NONE); x++) {
				if (displaySettlers != SettlersMode.NONE) {
					IMovable settler = map.getMovableAt(x, y);
					if (settler != null && (displaySettlers == SettlersMode.ALL || isSoilder(settler))) {
						settlerColor =
								context.getPlayerColor(settler.getPlayerId())
										.toShortColor(1);
						// don't search any more.
						displaySettlers = SettlersMode.NONE;
					}
				}

				if (displayOccupied == OccupiedAreaMode.BORDERS) {
					if (map.isBorder(x, y)) {
						byte player = map.getPlayerIdAt(x, y);
						Color playerColor = context.getPlayerColor(player);
						occupiedColor = playerColor.toShortColor(1);
						displayOccupied = OccupiedAreaMode.NONE;
					}
				} else if (displayOccupied == OccupiedAreaMode.AREA) {
					byte player = map.getPlayerIdAt(x, y);
					if (player >= 0 && !map.getLandscapeTypeAt(x, y).isBlocking) {
						Color playerColor = context.getPlayerColor(player);
						// Now add a landscape below that....
						Color landscape = getLandscapeForArea(map, context, mapminx, mapminy, mapmaxx, mapmaxy);
						playerColor = landscape.toGreyScale().overlay(playerColor);
						occupiedColor = playerColor.toShortColor(1);
						displayOccupied = OccupiedAreaMode.NONE;
					}
				}

				if (displayBuildings) {
					// TODO: Implement building shape.
					IMapObject object = map.getMapObjectsAt(x, y);
					while (object != null) {
						if (object.getObjectType() == EMapObjectType.BUILDING) {
							buildingColor = BLACK;
						}
						object = object.getNextObject();
					}
				}
			}
		}
		return settlerColor != TRANSPARENT ? settlerColor : buildingColor != TRANSPARENT ? buildingColor : occupiedColor;
	}

	/**
	 * TODO this needs a more complete implementation.
	 *
	 * @param landscapeType
	 * @return
	 */
	private static ELandscapeType getSimplifiedLandscapeType(ELandscapeType landscapeType)
	{
		switch (landscapeType) {
		case SNOW:
		case MOUNTAIN:
			return ELandscapeType.GRASS;
		case DESERT:
			// break;
		case DRY_GRASS:
			// break;
		case EARTH:
			// break;
		case FLATTENED:
			// break;
		case FLATTENED_DESERT:
			// break;
		case GRAVEL:
			// break;
		case MOOR:
			// break;
		case MOORBORDER:
			// break;
		case MOORINNER:
			// break;
		case MOUNTAINBORDER:
			// break;
		case MOUNTAINBORDEROUTER:
			// break;
		case RIVER1:
			// break;
		case RIVER2:
			// break;
		case RIVER3:
			// break;
		case RIVER4:
			// break;
		case SAND:
			// break;
		case SHARP_FLATTENED_DESERT:
			// break;
		case GRASS:
		case WATER1:
		case WATER2:
		case WATER3:
		case WATER4:
		case WATER5:
		case WATER6:
		case WATER7:
		case WATER8:
		default:
			return landscapeType;
		}
	}

	private boolean isSoilder(IMovable settler) {
		return soildertypes.contains(settler.getMovableType());
	}

	private static boolean isSoldier(IMovable settler)
	{
		switch (settler.getMovableType()) {
		case BOWMAN_L1:
		case BOWMAN_L2:
		case BOWMAN_L3:
		case PIKEMAN_L1:
		case PIKEMAN_L2:
		case PIKEMAN_L3:
		case SWORDSMAN_L1:
		case SWORDSMAN_L2:
		case SWORDSMAN_L3:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Stops the execution of this line loader.
	 */
	public void stop() {
		stopped = true;
	}
}