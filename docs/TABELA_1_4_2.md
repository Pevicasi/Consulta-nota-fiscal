# NF-e Fácil 1.4.2 — tabela e unidades automáticas (app)

Atende comentários 6452122094 e 6452162367 da issue #1. Scanner congelado no commit b0c865d: Java, CameraX, ML Kit, OCR, overlay e testes do scanner não mudam. Não representa aprovação do scanner pelo proprietário.

A camada `android-invoice.js` só executa em `appassets` com bridge nativa. Os quatro HTMLs públicos, fiscal-cost.js e Pages permanecem intactos. O motor fiscal original conserva rateios, impostos, descontos, conciliação, precisão em centavos e arredondamento monetário final.

Produto contém exclusivamente xProd como texto seguro, sem quantidade, avisos, auditoria, botões, formulário ou diálogo. Nomes quebram entre palavras. Quantidade mostra unidades físicas e unidade final, ou quantidade/unidade comercial quando não há equivalência comprovada. IPI permanece visível; colunas opcionais, ordem e rolagem horizontal permanecem.

A conversão usa contagem explícita em CX/CAIXA, FD/FARDO, PCT/PACOTE, padrões UN e X peso/volume, C/; ou equivalência tributável em UN com os dois produtos quantidade × preço conciliados com vProd. Peso/volume e QTD de caixas não viram fator. qCom em UN nunca é multiplicado pelo conteúdo da embalagem. Conflitos entre contagens documentadas não escolhem arbitrariamente um fator: custo e venda conservam unidade comercial com aviso curto na coluna de custo. Preferências antigas de conversão manual não entram no cálculo. Linhas do mesmo SKU têm conversões independentes.

Custo final = produto + componentes cobrados auditados − descontos, dividido pela quantidade física comprovada. Venda é calculada sobre o custo total não arredondado e markup atual (padrão 20%); só o valor monetário exibido é arredondado. CSV/impresso usam os mesmos resultados. Ausência de equivalência não impede preço por CX/FD/PCT; divergência fiscal continua impedindo preço como no motor auditado.

Fixture sintética `automatic-units.xml` e expectativas independentes JSON: 20 linhas, incluindo exemplos solicitados, qCom já em UN, tributação comercial repetida CX, IPI/ST/frete/desconto, dados ausentes/conflitantes e equivalência tributável. Exemplo: 10 CX × 12 = 120 UN, R$ 253,40 /120 = R$ 2,111666… custo; venda +20% R$ 2,53. Impostos: R$ 120 +12 IPI +6 ST +3 frete −1 desconto = R$ 140 /24 = R$ 5,83; venda +20% = R$ 7,00 (sem usar custo previamente arredondado).

Testes de navegador e WebView Android verificam resultados imediatos sem interação, DOM Produto igual ao nome, ausência de editor, persistência após recriação, markup editável, IPI, CSV, temas Claro/Escuro/Automático, 90/100/140/160%, retrato/paisagem, rolagem e capturas. Testes existentes de scanner, importação, OCR, ML Kit e funções nativas continuam executados, sem alterações no leitor.

Limites: XMLs e imagens reais do proprietário não foram disponibilizados. Emulador não equivale ao telefone físico. Impressão física e serviços fiscais externos reais não são cobertos. Publicação somente após Actions e Android aprovados; APK antigo removido somente depois de baixar e verificar a nova release.

Entrega: o job Android verifica e preserva o próprio APK instalado pelos testes, e a release usa esse binário (não outra recompilação). O download público é comparado com o artefato validado.
