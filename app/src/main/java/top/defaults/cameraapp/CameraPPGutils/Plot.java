package top.defaults.cameraapp.CameraPPGutils;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.TextureView;

import java.util.concurrent.CopyOnWriteArrayList;

class Plot
{
    private final TextureView chartTextureView;
    private final Paint paint = new Paint();
    private final Paint fillWhite = new Paint();

    // Plot Settings
    Plot(TextureView chartTextureView) {
        this.chartTextureView = chartTextureView;

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
        paint.setStrokeWidth(2);

        fillWhite.setStyle(Paint.Style.FILL);
        fillWhite.setColor(Color.WHITE);
    }

    // Draw the waveform
    void draw(CopyOnWriteArrayList<Measurement<Float>> data) {
        Canvas chartCanvas = chartTextureView.lockCanvas();

        if (chartCanvas == null) return;

        chartCanvas.drawPaint(fillWhite);
        Path graphPath = new Path();

        int dataAmount = data.size();
        float width = (float)chartCanvas.getWidth();
        float height = (float)chartCanvas.getHeight();

        // Draw the path
        graphPath.moveTo(0, height * (data.get(0).measurement));
        for (int dotIndex = 1; dotIndex < dataAmount; dotIndex++) {
            graphPath.lineTo(width * (dotIndex) / dataAmount,
                    height * (data.get(dotIndex).measurement));
        }
        chartCanvas.drawPath(graphPath, paint);
        chartTextureView.unlockCanvasAndPost(chartCanvas);
    }
}
