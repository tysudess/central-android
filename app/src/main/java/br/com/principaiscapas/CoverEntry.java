package br.com.principaiscapas;

import android.graphics.Bitmap;
import java.util.ArrayList;
import java.util.List;

public class CoverEntry {
    public NewspaperSource source;
    public boolean selected = true;
    public String status = "Aguardando";
    public int detectedPage = 0;
    public Bitmap coverBitmap;
    // Quando a capa vem do Gmail/Central Clipping, preservamos o arquivo original em cache.
    // O bitmap acima é apenas a prévia leve para a tela.
    public String originalImagePath = "";
    public int originalWidth = 0;
    public int originalHeight = 0;

    // v0.7.5.5: substituição manual opcional. Guardamos o resultado automático
    // anterior para permitir voltar sem refazer a busca e sem alterar os motores existentes.
    public boolean manualOverride = false;
    public String manualImagePath = "";
    public Bitmap automaticCoverBitmap;
    public String automaticOriginalImagePath = "";
    public int automaticOriginalWidth = 0;
    public int automaticOriginalHeight = 0;
    public String automaticStatus = "";
    public int automaticDetectedPage = 0;

    public List<CandidatePage> candidates = new ArrayList<>();
}
