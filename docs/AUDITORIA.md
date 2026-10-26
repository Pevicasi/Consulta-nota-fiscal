# Auditoria funcional e de interface — issue #1

Escopo: `index.html` e `consulta_precos_v15.html`, `v16`, `v17`, no commit original `e17043c`. Inspeção de todos os controles, funções JavaScript, persistência, saídas e destinos externos, seguida de execução no Chromium em 1280×800 e 390×844. A arquitetura Android foi escolhida após essa inspeção: manter a UI e os cálculos existentes em assets locais e substituir o leitor por CameraX/ML Kit.

## Inventário e correspondência no Android

| Funcionalidade existente | Comportamento / interface auditados | Implementação Android e validação |
|---|---|---|
| Selecionar/arrastar XMLs | Dois seletores, lote, XML com namespace, descarte de XML inválido | Mesmos parsers; seletor de documentos Android com seleção múltipla; importação sintética testada |
| Resumo e totais | Número, emissão, emitente, destinatário, chave, produtos, base ST, ST, percentual e valor NF | Mesmo HTML/cálculo; sem extração remota de notas |
| Itens e tributos | Quantidade, unidade, produto, custo, ST/unidade, custo+ST, preço sugerido, totais, GTIN, NCM, CST/CSOSN, CFOP e código interno | Mesmas 16 colunas; testes verificam 2 unidades × R$10 + R$4 ST → R$12/unidade e R$18 com margem 50% |
| Margem | Padrão 20%; mudança recalcula valores e cabeçalho | Mesmo código; valor e persistência testados |
| Várias notas | Abas, contador, resumo, seleção e nova consulta | Mesmo código; duas notas e arquivo inválido testados |
| Busca | Chave/número/conteúdo numérico e nome de arquivo numérico; erros sem XML/sem correspondência | Mesmo código e mensagens; resultados testados |
| Colunas | Expandir “Informações da tabela”, selecionar visibilidade, contador e arrastar cabeçalhos | Mesmo HTML; seleção e ordenação testadas; ordem salva |
| Sessão | Notas, aba ativa, margem e chaves em localStorage; ordem de colunas em chave separada | Armazenamento privado do WebView; recarga e nova consulta testadas |
| CSV | BOM UTF-8, delimitador `;`, aspas escapadas, nomes e números | Mesmo gerador; ponte converte blob e abre Salvar como; payload CSV testado |
| Impressão | Estilos de impressão, resumo e tabela; controles ocultos | PrintManager e adaptador de documento WebView; rota testada; impressão física precisa de aparelho/serviço |
| Código manual/clipboard | Digitar, colar, copiar, lista de chaves, remover, limpar, abrir portal | Mesmo histórico; clipboard nativo no app; regressão de copiar testada |
| Portal NF-e e ScanApp | Links externos; portal pode exigir captcha | Abrir navegador/aplicativo externo; não contornar autenticação |
| Leitura automática web | ZXing remoto; câmera traseira, rotações, recortes, validação módulo 11 | Substituída no app por CameraX + ML Kit CODE 128 embarcado, confirmação e rejeição de DV inválido |
| Foto/OCR web | Tesseract remoto, seleção móvel/redimensionável, OCR por linha | ML Kit OCR latino embarcado; câmera, galeria, rotação EXIF e editor de recorte; sem envio de imagens |
| Controles de câmera | No HTML atual não existem `torchBtn`, `switchCameraBtn`, `galleryBtn`, `barcodeImageInput`; rotinas legadas Html5Qrcode não são o leitor ativo | Guardas nos quatro registros opcionais eliminam exceção; no app controles são nativos, com foco por toque, zoom, flash e troca de câmera |
| Consulta de preços | Versões 15/16/17, consultas PR e RS, produtos/GTIN, combustíveis, localização, cidade, raio e dias | As três páginas são empacotadas, com acesso pelo menu Preços; geolocalização mediada por permissão Android |
| Cidade e mapas | Municípios IBGE, geocodificação Nominatim, links externos | Mesmo código; depende de internet e disponibilidade/CORS das APIs |
| Combustíveis | Seleção de tipo, aliases e apresentação/filtragem de resultados conforme versão | Modos e botões das três versões testados; nenhum endpoint governamental simulado é descrito como dado real |
| Autenticação RS | Token informado pelo usuário na Consulta 2 | Sem credencial embutida; exigência de token testada; consultas autenticadas não foram realizadas |

## Interface

- As quatro páginas foram abertas em desktop e celular, sem exceções JavaScript após a correção e sem transbordamento horizontal da página inicial. Capturas ficam em `test-results/` e nos artefatos de CI.
- A página principal usa cartões, cabeçalho claro, área de importação e botões que se reorganizam no celular. A tabela de 16 colunas tem rolagem horizontal própria; não foi reescrita nem simplificada.
- As páginas de preços possuem consultas em abas, modos Produtos/Combustíveis, cidade editável, raio/período e área de resultados. Sem permissão de localização, a mensagem existente aparece; a seleção manual continua disponível.
- Android acrescenta navegação Notas/Preços/Ler chave. O scanner exibe instruções, preview, zoom, flash, troca de câmera, galeria, foto/recorte e confirmação da chave. Insets do sistema são respeitados; cancelar a leitura não modifica a nota.

## Defeito torchBtn: impacto e correção

O primeiro `addEventListener` sobre `torchBtn === null` lançava exceção ao carregar o site, abortando todos os registros posteriores. Além da câmera, isso impedia copiar a chave pelo botão principal, limpeza do modal, eventos do recorte e salvamento/limpeza no encerramento da página. Apenas ocultar o erro no console não resolveria o fluxo.

A correção usa encadeamento opcional nos quatro registros de controles ausentes. O leitor ZXing ativo, o HTML, os estilos, os cálculos e todas as páginas de preços permanecem intactos. O teste abre a página sem esses elementos, exige ausência de exceções e exercita os handlers que antes não eram registrados (copiar, fechar e pagehide). Testes adicionais cobrem permissão negada, importação, cálculos, exportação e persistência.

## Limites observados

- O site não baixa XML a partir da chave. O aplicativo mantém essa regra, sem prometer consulta fiscal automática.
- Os cálculos de preço incluem ST por unidade e margem; frete, desconto e outros tributos não são rateados, conforme a observação já exibida pelo site.
- Localização, APIs públicas, captcha, permissões de clipboard/câmera, impressão e hardware são dependências externas. Testes com respostas simuladas cobrem a interface e não comprovam disponibilidade desses serviços.
- O leitor web ainda depende das bibliotecas remotas; o app não as utiliza. Funções legadas Html5Qrcode continuam sem uma biblioteca/controles no HTML atual; não foram reativadas como mudança incidental.
- O Android rejeita sequências de um único dígito, além de validar tamanho e módulo 11; o parser não junta linhas distintas nem escolhe silenciosamente entre chaves diferentes. Há confirmação humana, pois OCR pode errar mesmo com DV válido.
- OCR/barcode em emulador com imagens sintéticas não comprova qualidade de foco/flash de um telefone. Validação física permanece necessária para alcance, reflexos, dispositivos sem flash, permissão revogada e impressão real.
