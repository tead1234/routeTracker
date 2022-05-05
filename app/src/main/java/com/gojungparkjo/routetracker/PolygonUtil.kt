package com.gojungparkjo.routetracker

import ch.obermuhlner.math.big.DefaultBigDecimalMath
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.overlay.PolygonOverlay
import java.math.BigDecimal
import java.util.*

typealias Point = Pair<BigDecimal, BigDecimal>

fun PolygonOverlay.getConvexHull(): PolygonOverlay {

    val arr = this.coords.map { Point(it.latitude.toBigDecimal(), it.longitude.toBigDecimal()) }
        .toMutableList()
    var bottomPoint = Point(90.0.toBigDecimal(), 180.0.toBigDecimal())
    for (point in arr) {
        if (point.second < bottomPoint.second) {
            bottomPoint = point
        } else if (point.second == bottomPoint.second) {
            if (point.first < bottomPoint.first) {
                bottomPoint = point
            }
        }
    }
    arr.sortWith(kotlin.Comparator { o1, o2 ->
        val result = ccw(bottomPoint, o1, o2)
        if (result > 0) return@Comparator -1
        else if (result < 0) return@Comparator 1
        else {
            if (dist(bottomPoint, o1) > dist(bottomPoint, o2)) {
                return@Comparator 1
            }
        }
        return@Comparator -1
    })

    val stack = Stack<Point>()
    stack.add(bottomPoint)
    for (i in 1 until arr.size) {
        while (stack.size > 1 && ccw(stack[stack.size - 2], stack[stack.size - 1], arr[i]) <= 0) {
            stack.pop()
        }
        stack.add(arr[i])
    }
    return PolygonOverlay(stack.toList().map { LatLng(it.first.toDouble(), it.second.toDouble()) })
}

fun ccw(p1: Point, p2: Point, p3: Point): Int {
    /*
    x1 x2 x3 x1
    y1 y2 y3 y1
     */
    val result =
        (p1.first * p2.second + p2.first * p3.second + p3.first * p1.second) - (p2.first * p1.second + p3.first * p2.second + p1.first * p3.second)
    if (result > BigDecimal.ZERO) return 1
    if (result < BigDecimal.ZERO) return -1
    return 0
}

fun dist(p1: Point, p2: Point): BigDecimal {
    return (p2.first - p1.first) * (p2.first - p1.first) + (p2.second - p1.second) * (p2.second - p1.second)
}

fun PolygonOverlay.orientedMinimumBoundingBox(): PolygonOverlay {
    val convexHull = this.getConvexHull().coords.map {
        Point(
            it.latitude.toBigDecimal(),
            it.longitude.toBigDecimal()
        )
    }.toTypedArray()
    val alignPointIndex = computeAlignPointIndex(convexHull)
    val r = computeAlignedPolygon(convexHull,alignPointIndex)
    val pob = computePointOfBound(r)
    val angle = computeEdgeAngleRad(convexHull,alignPointIndex)
    val omb = rotatePolygon(pob,angle,convexHull[alignPointIndex])
    return PolygonOverlay(omb.map { LatLng(it.first.toDouble(),it.second.toDouble()) })
}

fun computeAlignPointIndex(pg: Array<Point>): Int {
    var minArea = BigDecimal.TEN
    var minAreaIndex = -1
    for (i in pg.indices) {
        val r = computeAlignedPolygon(pg, i)
        val pob = computePointOfBound(r)
        val area = computeAreaOfPointOfBound(pob)
        if (area < minArea) {
            minArea = area
            minAreaIndex = i
        }
    }
    return minAreaIndex
}

fun computeAreaOfPointOfBound(pg:Array<Point>): BigDecimal {
    return (pg[3].first - pg[0].first) * (pg[3].second - pg[0].second)
}

fun computePointOfBound(pg: Array<Point>): Array<Point> {
    var minX = (90.0).toBigDecimal()
    var minY = (180.0).toBigDecimal()
    var maxX = (-90.0).toBigDecimal()
    var maxY = (-180.0).toBigDecimal()
    for (p in pg) {
        val x = p.first
        val y = p.second
        minX = if (minX > x) x else minX
        minY = if (minY > y) y else minY
        maxX = if (maxX < x) x else maxX
        maxY = if (maxY < y) y else maxY
    }
    return arrayOf(
        Point(minX, minY), Point(minX, maxY),
        Point(maxX, minY), Point(maxX, maxY)
    )
}

fun computeAlignedPolygon(pg: Array<Point>, i: Int): Array<Point> {
    val angle = computeEdgeAngleRad(pg, i)
    return rotatePolygon(pg, -angle, i)
}

fun computeEdgeAngleRad(
    points: Array<Point>, index: Int
): BigDecimal {
    val i1 = (index + 1) % points.size
    val p0 = points[index]
    val p1 = points[i1]
    val dx = p1.first - p0.first
    val dy = p1.second - p0.second
    return DefaultBigDecimalMath.atan2(dy, dx)
}

/**
 * Rotates a polygon by an angle alpha. <b>The polygon is passed by
 * reference.</b>
 *
 * @param pg       The polygon to be rotated
 * @param rotAngle The rotation angle in <b>radians</b>.
 * @param centroid The centroid of the polygon.
 * @param original When rotating a polygon with a mouse, the original must be
 *                 maintained.
 */

fun rotatePolygon(
    pg: Array<Point>,
    rotAngle: BigDecimal,
    i: Int
): Array<Point> {
    var x: BigDecimal
    var y: BigDecimal
    return Array(pg.size) {
        x = pg[it].first - pg[i].first
        y = pg[it].second - pg[i].second
        val nx =
            pg[i].first + (x * DefaultBigDecimalMath.cos(rotAngle) - y * DefaultBigDecimalMath.sin(
                rotAngle
            ))
        val ny =
            pg[i].second + (x * DefaultBigDecimalMath.sin(rotAngle) + y * DefaultBigDecimalMath.cos(
                rotAngle
            ))
        Point(nx, ny)
    }
}
fun rotatePolygon(
    pg: Array<Point>,
    rotAngle: BigDecimal,
    centroid: Point
): Array<Point> {
    var x: BigDecimal
    var y: BigDecimal
    return Array(pg.size) {
        x = pg[it].first - centroid.first
        y = pg[it].second - centroid.second
        val nx =
            centroid.first + (x * DefaultBigDecimalMath.cos(rotAngle) - y * DefaultBigDecimalMath.sin(
                rotAngle
            ))
        val ny =
            centroid.second + (x * DefaultBigDecimalMath.sin(rotAngle) + y * DefaultBigDecimalMath.cos(
                rotAngle
            ))
        Point(nx, ny)
    }
}

/**
 * Finds the centroid of a polygon with integer verticies.
 *
 * @param pg The polygon to find the centroid of.
 *
 * @return The centroid of the polygon.
 */
fun polygonCenterOfMass(pg: Array<Point>): Point {

    val N: Int = pg.size
    val polygon = Array<Point>(N) {
        Point(pg[it].first, pg[it].second)
    }
    var cx = BigDecimal.ZERO
    var cy = BigDecimal.ZERO
    val A = PolygonArea(polygon, N)
    var i: Int
    var j: Int
    var factor = BigDecimal.ZERO
    i = 0
    while (i < N) {
        j = (i + 1) % N
        factor = polygon[i].first * polygon[j].second - polygon[j].first * polygon[i].second
        cx += (polygon[i].first + polygon[j].first) * factor
        cy += (polygon[i].second + polygon[j].second) * factor
        i++
    }
    factor = 1.0.toBigDecimal() / (6.0.toBigDecimal() * A)
    cx *= factor
    cy *= factor
    return Point(
        cx.abs(),
        cy.abs()
    )
}

/**
 * Computes the area of any two-dimensional polygon.
 *
 * @param polygon The polygon to compute the area of input as an array of points
 * @param N       The number of points the polygon has, first and last point
 * inclusive.
 *
 * @return The area of the polygon.
 */
fun PolygonArea(polygon: Array<Point>, N: Int): BigDecimal {
    var i: Int
    var j: Int
    var area = BigDecimal.ZERO
    i = 0
    while (i < N) {
        j = (i + 1) % N
        area += polygon[i].first * polygon[j].second
        area -= polygon[i].second * polygon[j].first
        i++
    }
    area /= 2.0.toBigDecimal()
    return area.abs()
}