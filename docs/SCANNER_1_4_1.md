# Scanner 1.4.1 — estados do vídeo aprovado

Implementa a descrição da referência na issue #1, comentários 6450179475 e 6450211078, com precedência do último para as cores sem detecção. O MP4 Screenrecorder-2026-10-27-15-18-17-577.mp4 não foi disponibilizado ao executor; não se afirma tê-lo recebido/assistido. A referência usada é a descrição quadro a quadro registrada na issue e a instrução desta conversa.

- Sem código: guia fixo, quatro cantos azul/vermelho/amarelo/verde, sem senoides, gradiente, linha de varredura ou loop de animação.
- Candidato real: quatro cantos azuis pequenos ajustados às coordenadas do detector. Etiquetas curtas CODE128 e formatos lineares EAN/UPC/ITF/CODE39/CODE93/Codabar podem ser acompanhados; sua geometria nunca os transforma em NF-e.
- Chave NF-e: verde somente após filtro de formato CODE128, 44 dígitos e validação fiscal/DV. Diálogo explícito; múltiplas chaves detectadas exigem escolha, sem alvo implícito. OCR conserva sua validação e fallback existentes.
- Perda ou “Ler novamente”: retorna ao guia fixo multicolorido. Cantos não se movem sem uma nova posição do detector; alvos expiram. Pausa/retomada, foto, confirmação e destruição não deixam loop de renderização. Coordenadas CameraX/crop/rotação/espelho preservadas.

O caminho da câmera agora é `scanLive`: InputImage/media Image → um passe ML Kit → resultado imediato. Não cria Bitmap nem executa ROIs; código curto/potencial chega ao overlay sem esperar refinamento. Chave válida não aguarda OCR/animação. Próximos frames renovam a geometria. Para escolha explícita de múltiplas chaves, acompanhamento limitado a 5 Hz mantém o alvo selecionado atualizado sem reabrir confirmação. Galeria/recorte conserva o refinamento limitado anterior, sem alteração do editor ou descarte prematuro de imagens.

Testes Android usam o mesmo scanner configurado na produção para candidatos curtos/EAN em duas posições; capturam guia parado (inclusive com animações do Android habilitadas), azul e verde em claro/escuro, fontes 90%/160%, retorno e redução de movimento. Cantos são conferidos por pixels; geometria vem do ML Kit de quadros sintéticos exibidos no preview. Isso não equivale à câmera física lendo o DANFE do proprietário. Os testes existentes de ML Kit/OCR offline, múltiplos códigos, DV, seleção, CameraX, foto e galeria permanecem.

Benchmark pareado compara a implementação 1.4.0 (snapshot test-only) e `scanLive`, com modelo aquecido, ordem alternada e três pares por sete variantes sintéticas. Publicar somente números do artefato concluído, incluindo regressões e limites; não prometer velocidade no aparelho físico.

Nesta atualização não se alteram tabela/IPI/cálculos, `android-invoice.js`, `android-appearance.js`, motor fiscal ou HTMLs públicos. Pacote original, versão 1.4.1/código 9, assinatura debug automática pessoal e quatro ABIs. Main sem PR; APK publicado somente após testes Android; conferir download/capturas e retirar APK anterior para cumprir política de APK único.
