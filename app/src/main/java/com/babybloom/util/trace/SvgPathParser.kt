package com.babybloom.util.trace

import androidx.compose.ui.geometry.Offset
import kotlin.math.*

object SvgPathParser {
    private const val CURVE_STEPS = 32

    fun parseSubpaths(pathData: String): List<List<Offset>> {
        if (pathData.isBlank()) return emptyList()
        val tokens  = tokenize(pathData)
        val result  = mutableListOf<MutableList<Offset>>()
        var current = mutableListOf<Offset>()
        var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
        var lcpX = 0f; var lcpY = 0f; var lqpX = 0f; var lqpY = 0f
        var lastCmd = 'M'; var ti = 0

        fun commit() { if (current.size >= 2) result.add(current); current = mutableListOf() }
        fun add(x: Float, y: Float) { val p = Offset(x, y); if (current.isEmpty() || current.last() != p) current.add(p) }
        fun hasNum() = ti < tokens.size && !tokens[ti][0].isLetter()
        fun nextFloat(): Float = tokens[ti++].toFloat()
        fun nextFlag(): Boolean {
            val tok = tokens[ti]
            return if (tok.length == 1) { ti++; tok == "1" }
            else { val f = tok[0] == '1'; tokens[ti] = tok.substring(1); f }
        }
        fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            val x0 = cx; val y0 = cy
            for (s in 1..CURVE_STEPS) { val t = s.toFloat()/CURVE_STEPS; val mt = 1f-t
                add(mt*mt*mt*x0+3f*mt*mt*t*x1+3f*mt*t*t*x2+t*t*t*x3, mt*mt*mt*y0+3f*mt*mt*t*y1+3f*mt*t*t*y2+t*t*t*y3) }
            lcpX = x2; lcpY = y2; cx = x3; cy = y3
        }
        fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
            val x0 = cx; val y0 = cy
            for (s in 1..CURVE_STEPS) { val t = s.toFloat()/CURVE_STEPS; val mt = 1f-t
                add(mt*mt*x0+2f*mt*t*x1+t*t*x2, mt*mt*y0+2f*mt*t*y1+t*t*y2) }
            lqpX = x1; lqpY = y1; cx = x2; cy = y2
        }
        fun arcTo(rx0: Float, ry0: Float, xRot: Float, largeArc: Boolean, sweep: Boolean, x1: Float, y1: Float) {
            val x0 = cx; val y0 = cy
            if (x0==x1 && y0==y1) { cx=x1; cy=y1; return }
            if (rx0==0f || ry0==0f) { add(x1,y1); cx=x1; cy=y1; return }
            val phi=xRot*PI.toFloat()/180f; val cosP=cos(phi); val sinP=sin(phi)
            val dx2=(x0-x1)/2f; val dy2=(y0-y1)/2f
            val x1p=cosP*dx2+sinP*dy2; val y1p=-sinP*dx2+cosP*dy2
            var rx=abs(rx0); var ry=abs(ry0); val x1pSq=x1p*x1p; val y1pSq=y1p*y1p
            var rxSq=rx*rx; var rySq=ry*ry
            val lambda=x1pSq/rxSq+y1pSq/rySq
            if (lambda>1f) { val sqL=sqrt(lambda); rx*=sqL; ry*=sqL; rxSq=rx*rx; rySq=ry*ry }
            val num=(rxSq*rySq-rxSq*y1pSq-rySq*x1pSq).coerceAtLeast(0f)
            val den=(rxSq*y1pSq+rySq*x1pSq).coerceAtLeast(1e-10f)
            val sq=sqrt(num/den); val k=if(largeArc==sweep) -sq else sq
            val cxp=k*rx*y1p/ry; val cyp=-k*ry*x1p/rx
            val ccx=cosP*cxp-sinP*cyp+(x0+x1)/2f; val ccy=sinP*cxp+cosP*cyp+(y0+y1)/2f
            val ux=(x1p-cxp)/rx; val uy=(y1p-cyp)/ry; val vx=(-x1p-cxp)/rx; val vy=(-y1p-cyp)/ry
            var theta1=svgAngle(1f,0f,ux,uy); var dTheta=svgAngle(ux,uy,vx,vy)
            if (!sweep && dTheta>0f) dTheta-=2f*PI.toFloat()
            if (sweep && dTheta<0f) dTheta+=2f*PI.toFloat()
            // CRITICAL: sampling runs unconditionally (arc bug fix)
            val steps=max(CURVE_STEPS,(abs(dTheta)*12f).toInt())
            for (s in 1..steps) { val a=theta1+dTheta*s.toFloat()/steps
                add(cosP*rx*cos(a)-sinP*ry*sin(a)+ccx, sinP*rx*cos(a)+cosP*ry*sin(a)+ccy) }
            cx=x1; cy=y1
        }

        while (ti < tokens.size) {
            if (tokens[ti][0].isLetter()) lastCmd = tokens[ti++][0]
            when (lastCmd) {
                'M' -> { commit(); cx=nextFloat(); cy=nextFloat(); sx=cx; sy=cy; add(cx,cy); lastCmd='L' }
                'm' -> { commit(); cx+=nextFloat(); cy+=nextFloat(); sx=cx; sy=cy; add(cx,cy); lastCmd='l' }
                'L' -> { val x=nextFloat(); val y=nextFloat(); add(x,y); cx=x; cy=y }
                'l' -> { val x=cx+nextFloat(); val y=cy+nextFloat(); add(x,y); cx=x; cy=y }
                'H' -> { cx=nextFloat(); add(cx,cy) }; 'h' -> { cx+=nextFloat(); add(cx,cy) }
                'V' -> { cy=nextFloat(); add(cx,cy) }; 'v' -> { cy+=nextFloat(); add(cx,cy) }
                'C' -> { val x1=nextFloat();val y1=nextFloat();val x2=nextFloat();val y2=nextFloat();val x=nextFloat();val y=nextFloat(); cubicTo(x1,y1,x2,y2,x,y) }
                'c' -> { val x1=cx+nextFloat();val y1=cy+nextFloat();val x2=cx+nextFloat();val y2=cy+nextFloat();val x=cx+nextFloat();val y=cy+nextFloat(); cubicTo(x1,y1,x2,y2,x,y) }
                'S' -> { val x2=nextFloat();val y2=nextFloat();val x=nextFloat();val y=nextFloat(); cubicTo(2f*cx-lcpX,2f*cy-lcpY,x2,y2,x,y) }
                's' -> { val x2=cx+nextFloat();val y2=cy+nextFloat();val x=cx+nextFloat();val y=cy+nextFloat(); cubicTo(2f*cx-lcpX,2f*cy-lcpY,x2,y2,x,y) }
                'Q' -> { val x1=nextFloat();val y1=nextFloat();val x=nextFloat();val y=nextFloat(); quadTo(x1,y1,x,y) }
                'q' -> { val x1=cx+nextFloat();val y1=cy+nextFloat();val x=cx+nextFloat();val y=cy+nextFloat(); quadTo(x1,y1,x,y) }
                'T' -> { quadTo(2f*cx-lqpX,2f*cy-lqpY,nextFloat(),nextFloat()) }
                't' -> { val x=cx+nextFloat();val y=cy+nextFloat(); quadTo(2f*cx-lqpX,2f*cy-lqpY,x,y) }
                'A' -> { val rx=nextFloat();val ry=nextFloat();val r=nextFloat();val la=nextFlag();val sw=nextFlag();val x=nextFloat();val y=nextFloat(); arcTo(rx,ry,r,la,sw,x,y) }
                'a' -> { val rx=nextFloat();val ry=nextFloat();val r=nextFloat();val la=nextFlag();val sw=nextFlag();val x=cx+nextFloat();val y=cy+nextFloat(); arcTo(rx,ry,r,la,sw,x,y) }
                'Z','z' -> { if (current.isNotEmpty()) add(sx,sy); cx=sx; cy=sy }
            }
            if (!hasNum()) continue
        }
        commit(); return result
    }

    private fun tokenize(data: String): MutableList<String> {
        val result = mutableListOf<String>(); val buf = StringBuilder(); var hasDot = false
        fun flush() { if (buf.isNotEmpty()) { result.add(buf.toString()); buf.clear(); hasDot = false } }
        for (c in data) when {
            c.isLetter()                  -> { flush(); result.add(c.toString()) }
            c=='-'||c=='+'               -> { val p=buf.lastOrNull(); if (buf.isNotEmpty()&&p!='e'&&p!='E') flush(); buf.append(c); hasDot=false }
            c=='.'                        -> { if (hasDot) flush(); hasDot=true; buf.append(c) }
            c.isDigit()                   -> buf.append(c)
            c=='e'||c=='E'               -> buf.append(c)
            c==','||c.isWhitespace()     -> flush()
        }
        flush(); return result
    }

    private fun svgAngle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val dot=ux*vx+uy*vy; val len=sqrt((ux*ux+uy*uy)*(vx*vx+vy*vy)).coerceAtLeast(1e-10f)
        val a=acos((dot/len).coerceIn(-1f,1f)); return if (ux*vy-uy*vx<0f) -a else a
    }
}