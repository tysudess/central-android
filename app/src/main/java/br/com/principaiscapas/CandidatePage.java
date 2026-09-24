package br.com.principaiscapas;

import java.io.File;

public class CandidatePage {
    public int pageNumber;
    public int score;
    public File imageFile;
    public String recognizedText = "";

    // v0.7.7.5: quando a candidata veio de um PDF anexado (caso do Valor),
    // preservamos também o PDF original dentro do armazenamento privado do app.
    public File sourcePdfFile;
    public String sourceFilename = "";

    // v0.7.6.0: a revisão preserva todas as posições que o Gmail enviou,
    // mesmo quando uma página não pôde ser aberta/resolvida.
    public boolean available = true;
    public String errorMessage = "";
}
