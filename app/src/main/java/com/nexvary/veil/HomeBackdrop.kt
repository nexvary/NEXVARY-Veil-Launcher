package com.nexvary.veil

import android.graphics.*
import android.graphics.drawable.Drawable

/** Resolution-independent wallpaper; contains no external assets or data. */
class HomeBackdrop(private val style: Int) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w=bounds.width().toFloat();val h=bounds.height().toFloat()
        val colors=when(style) {
            1 -> intArrayOf(0xff271d40.toInt(),0xff795968.toInt(),0xff111c32.toInt())
            2 -> intArrayOf(0xff303c48.toInt(),0xff172330.toInt(),0xff080e18.toInt())
            else -> intArrayOf(0xff133746.toInt(),0xff21656d.toInt(),0xff0b172c.toInt())
        }
        paint.shader=LinearGradient(0f,0f,w,h,colors,null,Shader.TileMode.CLAMP)
        canvas.drawRect(bounds,paint);paint.shader=null
        paint.color=0x162ee6d3
        canvas.drawOval(-w*.7f,h*.15f,w*1.25f,h*1.05f,paint)
        paint.color=0x20050e25
        canvas.drawOval(w*.2f,-h*.1f,w*1.8f,h*1.4f,paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter;invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity()=PixelFormat.OPAQUE
}
