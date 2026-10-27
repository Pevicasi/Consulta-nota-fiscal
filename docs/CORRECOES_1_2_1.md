# NF-e Fácil 1.2.1 — correções após teste físico

Implementa conjuntamente os três pontos do [comentário 6439169051 da issue #1](https://github.com/Pevicasi/Consulta-nota-fiscal/issues/1#issuecomment-6439169051). Versão 1.2.1, código 5, nome NF-e Fácil e pacote br.com.pevicasi.nfefacil. Assinatura debug pessoal automática, APK universal, sem alteração de ABI/otimização ou exigência de Secrets permanentes. Releases 1.1.0 e 1.2.0 são preservadas.

## Importação múltipla Android

A implementação anterior entregava o resultado a FileChooserParams.parseResult e dependia de o Chromium abrir content://, embora allowContentAccess estivesse false. Além disso, o fluxo HTML podia substituir a coleção e uma falha em file.text() escapava do tratamento individual.

O Android agora coleta todas as URIs de ClipData e data, elimina URIs repetidas e lê somente os documentos content:// retornados pelo seletor ACTION_OPEN_DOCUMENT, usando os grants SAF e ContentResolver. Não há nova permissão de armazenamento. allowContentAccess e allowFileAccess continuam false. O callback do file input é encerrado com null para liberar/resetar o chooser; os textos selecionados são entregues por JSON à ponte da página local Notas. Não se expõe acesso genérico a URIs ao JavaScript.

A leitura ocorre fora da UI, por documento, com limites explícitos: 64 documentos, 4 MiB por arquivo e 8 MiB por seleção. Falha de permissão/provedor, arquivo vazio e XML inválido não interrompem os demais nem removem notas anteriores; as mensagens indicam os nomes e contagens. A área de status é movida para a tela ativa: não fica escondida dentro do cartão de upload quando já há notas abertas. Cancelar mantém a sessão. Seleção sem URIs informa que o provedor não devolveu arquivos. Esquemas file:// e URLs externas não são abertos como documentos.

O importador Android preserva a coleção e nota ativa, acrescenta as válidas e elimina repetições pelos dados fiscais completos, inclusive após restauração de sessão. O botão “Adicionar XMLs” aparece na área das notas abertas. Os dois inputs originais continuam disponíveis em suas telas. Repetir uma seleção não duplica abas. Uma ação explícita “Nova consulta” continua iniciando outra coleção. Não foram alterados os quatro HTMLs públicos.

## Botão e tabela

Somente o asset Android trata a.btn-link como botão: fundo azul contrastante, texto branco, borda, padding, foco visível e altura mínima de 48 px CSS. O link oficial continua HTTPS e é aberto externamente; links comuns mantêm aparência de links.

Quantidade recebe largura mínima de 12 em; os rótulos curtos Item, Produto e Quantidade ficam em uma linha. Cabeçalhos extensos continuam quebráveis; células de produtos usam quebra em limites de palavras, com fallback para conteúdo sem espaços. Tabela e preferências de ordem/ocultação das 16 colunas mantêm rolagem horizontal. Não se reduziu a fonte.

## Verificação

- 15 testes web: regressões anteriores e importação Android por lotes, inválidos/vazios/erros/repetições, restauração, botão, foco, URL, cabeçalhos e preferências de coluna. Escalas 90/100/140/160, claro/escuro, retrato/paisagem; os testes de aparência anteriores continuam verificando as quatro páginas.
- 8 unitários Java e 5 de política de assinatura.
- 27 instrumentados: 24 anteriores + 3 de ImportRepairTest. Incluem provedor DocumentsProvider real no APK de instrumentação, UI DocumentsUI/SAF acionada por toque no WebView, seleções simultâneas de 2, 3 e 7 documentos, ClipData, conteúdo lido via ContentResolver, notas/valores, mistura inválida/vazia, repetição após recriação, cancelamento/resultado sem URIs/sem permissão, arquivos fora de SAF recusados, layouts Android e clique que abre a URL oficial externamente.
- O provedor e UiAutomator pertencem somente ao APK de instrumentação; não são incluídos no aplicativo pessoal.
- Lint/build, instrumentação executada no Actions e download/verificação do APK final são exigidos antes da aprovação. Resultados reais, contagens, capturas, SHA-256 e links ficam na issue #1 e release 1.2.1.

Limites: emulador Android/WebView, XMLs sintéticos e provedor SAF controlado não substituem telefone físico, provedores externos Google Drive/gerenciadores de fabricantes, câmera/foco/flash físicos, impressão real, APIs governamentais ao vivo ou Play Protect. Certificados debug podem variar; nenhuma desinstalação, exclusão de APK antigo ou limpeza de dados é automática.
