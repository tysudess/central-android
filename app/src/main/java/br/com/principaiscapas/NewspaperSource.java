package br.com.principaiscapas;

import java.util.ArrayList;
import java.util.List;

public class NewspaperSource {
    public enum Type { PDF, IMAGE, HTML }

    public String name;
    public Type type;
    public String url;
    // Fontes alternativas, em ordem de prioridade. Se uma falhar, o app tenta a próxima.
    public List<String> urls = new ArrayList<>();
    public List<String> keywords = new ArrayList<>();
    // Frases fortes do cabeçalho/masthead. São verificadas apenas no topo da imagem.
    public List<String> mastheads = new ArrayList<>();
    public boolean enabled;
    public String followSelector = "";
    public String followText = "";
    public String imageSelector = "";
    // Pequena margem branca permitida no PDF para capas muito estreitas.
    public int pdfMarginPercent = 0;
}
