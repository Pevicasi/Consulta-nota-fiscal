# 1.3.1

- Corrige também a largura mínima herdada do nome do produto; testes verificam conteúdo dentro da célula.
- Colunas compactas Item/Quantidade e mais área útil para Produto no Android.
- Formação de custo com IPI, ST/FCP-ST, despesas, descontos, centavos e conciliação.
- Conversão explícita de embalagens para unidade final, persistida por produto.
- CSV e impressão usam a mesma metodologia; ambiguidades não geram preço final.

# 1.2.1

- Corrige importação múltipla SAF no Android, preservando notas e informando erros por arquivo.
- Adiciona XMLs à coleção aberta sem duplicar seleções repetidas.
- Restaura o botão Abrir site em todos os temas e mantém Quantidade/Produto/Item sem quebra interna.
- Preserva APKs anteriores e amplia testes reais de SAF/layout no Android.

# Registro de alterações

## 1.2.0 — Fontes e modo noturno / issue #1

- Controles A−, A+ e Restaurar padrão nas telas Notas/Preços e scanner; escala de 90% a 160%, em passos de 10%, padrão 100%, com nomes acessíveis, indicação de percentual e limites desabilitados.
- Tema Claro, Escuro ou Automático seguindo o Android, com preferência persistida junto da escala em SharedPreferences. Mudanças imediatas sem recriar a Activity, reiniciar câmera, recarregar consulta ou perder XML/estado da nota. Alterações de tema do sistema em Automático atualizam as telas em uso.
- Tema/fontes também em navegação, câmera, controles, mensagens, menus e diálogos próprios; scanner e confirmação permitem rolagem quando necessário. Conteúdo local do WebView recebe um asset exclusivo do app: cartões adaptativos, texto quebrável, tabela de 16 colunas com rolagem horizontal e desconto verde contrastante. Impressão mantém cores claras e o estilo original.
- Os quatro HTMLs do site público continuam inalterados nesta atualização; o asset recusa origens externas e não é carregado no GitHub Pages.
- Mantidos barcode/ML Kit/OCR, importação XML, desconto, acréscimo, CSV, impressão, dados existentes, nome NF-e Fácil e pacote original. Versão 1.2.0, código 4, APK universal com debug automático para uso pessoal. Removida a opção de filtrar ABI introduzida anteriormente, conforme decisão posterior do proprietário; não há otimização de tamanho nem bibliotecas removidas.
- Adicionados 3 testes web de aparência (13 no total) e 4 testes instrumentados (24 no total), com capturas Android, limites/reset, persistência, tema do sistema, combinações de escala/tema, nota, preços e confirmação do scanner. CI verifica nome/pacote/versão, ABIs universais, assinatura v2, identidade dos HTMLs e dos dois assets Android, e publica checksum SHA-256 do APK em anotação.
- APKs anteriores preservados até aprovação do proprietário. Assinatura debug pode variar entre execuções; não é garantida atualização sobre certificado diferente, não há desinstalação/limpeza automática e não se promete remover alertas Play Protect. Evidências e limites em docs/APARENCIA.md e na issue #1.

## 1.1.0 — Auditoria integral da issue #1

- Corrigida falha reproduzida: o cartão de desconto permanecia com `hidden` mesmo para `ICMSTot/vDesc` positivo. A renderização agora controla o atributo correto; total oficial zero continua autoritativo. Na ausência do total, usa somente a soma de `prod/vDesc`, sem somar ambos nem inferir `vProd - vNF`.
- Adicionada regressão web e teste instrumentado sobre o HTML realmente embarcado: desconto positivo, zero, ausente, fallback de itens, frete/outros ajustes, valor monetário/visibilidade e verde no WebView. Mantidos acréscimo padrão de 20%, XMLs, navegação, CSV e impressão.
- Corrigida a mensagem de status da Consulta 2 nas versões 15/16/17, usando `setStatus` em vez da propriedade global `window.status`. Regressão de falha de rede adicionada.
- Acrescentada otimização optativa por ABI ARM confirmada do aparelho, sem mudar nome/pacote nem remover ML Kit/OCR. Build padrão e instrumentação continuam universais. ABI do aparelho ainda deve ser confirmada; não se presume arm64.
- Verificador do APK agora exige nome NF-e Fácil, pacote original, versão e ABIs esperadas. CI registra tamanho bruto, SHA-256 e decomposição por ABI; não compara ZIP de artefato com APK bruto.
- Atualizada documentação para a decisão vigente de APK pessoal com debug automático, sem variante QA de entrega e sem exigir Secrets permanentes. Riscos de atualização, backup e Play Protect permanecem explícitos.
- Resultados e pendências em `docs/AUDITORIA_1_1_0.md`. Não foram apagados artefatos antigos. Aprovação depende do CI e do APK do commit final da main, além da confirmação de ABI para otimização. Não houve teste físico do aparelho.

## 1.1.0 — Continuação da issue #1

- Investigado o conflito de instalação: APKs 1.0.1 com mesmo pacote/versão tinham certificados Android Debug diferentes. Documentadas impressões SHA-256, impossibilidade de atualização entre assinaturas incompatíveis e migração manual com backup verificado, sem limpeza automática de dados.
- Separados QA (`br.com.pevicasi.nfefacil.qa`) e distribuição (pacote original, release não depurável). Preparado workflow de distribuição manual com Secrets de chave persistente, impressão de certificado aprovada, verificação antes/depois do build e remoção de material temporário. Distribuição bloqueia sem a configuração do responsável; não gera fallback de chave. Actions fixadas por SHA e cache desativado no job de assinatura.
- Registrada investigação de Play Protect, permissões, dependências, práticas de segurança e limites de APK externo. Corrigida comparação exata da origem de geolocalização. Não foi desativada proteção nem prometida remoção de alerta.
- Adicionada validação estrutural compartilhada de chaves: UF, mês, CNPJ/CPF, modelo NF-e/NFC-e 55/65, número, tipo de emissão e DV. CODE 128 aceita somente dígitos/espaços; outros formatos, URLs e números de pagamento não são normalizados em chaves.
- Adicionadas releituras limitadas de regiões sobrepostas com o mesmo ML Kit: detectores podem fundir códigos alinhados e ocultar a chave no quadro completo. Resultados são reunidos e coordenadas locais retornam ao quadro original; bordas recortadas não são usadas para inventar geometria. Processamento fora da UI, uma região por vez, no máximo 14 por quadro com detecção potencial; sem busca cega nem troca de modelo.
- Todos os resultados do detector são examinados e deduplicados. Havendo várias chaves válidas, confirmação começa desabilitada e exige seleção explícita. A moldura acompanha somente o candidato selecionado, inclusive durante a confirmação. OCR segue a mesma política e permanece disponível; falha de OCR não descarta código válido do recorte. Resultados antigos em voo são descartados após nova leitura ou troca/saída da foto.
- Adicionados testes unitários estruturais, imagens sintéticas com múltiplos códigos/formatos, chaves inválidas e ambíguas, OCR com múltiplas chaves, escolha/restauração e testes de proteção da assinatura. ML Kit, animação, foco, zoom, lanterna e site preservados.

## 1.0.1 — Android / issue #2

- Adicionada moldura de cantos arredondados com interpolação sobre a geometria real do ML Kit, transformação CameraX de rotação, viewport/recorte e espelhamento da prévia. Sem detecção, usa somente guia estática; geometria antiga expira. Animação respeita a desativação de animações do sistema.
- Mantidos os modelos embarcados CODE 128/OCR, foco, pinça, zoom manual, lanterna e recorte. Sem zoom automático para evitar interferência nos controles manuais.
- Chaves válidas exibidas em texto selecionável, com botões explícitos “Confirmar chave” e “Ler novamente”, bloqueio de confirmações duplicadas e restauração do diálogo na recriação da tela.
- Adicionados testes instrumentados de confirmação/retorno, nova leitura, recriação e transformação de coordenadas em quatro rotações com recorte e espelhamento. Site preservado.

## 1.0.0 — Android / issue #1

- Auditado o site atual, suas quatro páginas, interface móvel/desktop, dependências e limitações em `docs/AUDITORIA.md`.
- Adicionado aplicativo independente em `android/`, mantendo as páginas HTML originais como fonte única dos cálculos e da interface. Os assets são gerados no build.
- Implementados CameraX, foco contínuo e por toque, zoom, flash disponível por hardware, troca de câmera, galeria, orientação EXIF e editor de recorte.
- Implementados ML Kit CODE 128 e OCR latino com modelos locais, validação da chave de 44 dígitos por módulo 11, deduplicação e confirmação do resultado.
- Integrados seletor de XMLs, clipboard Android, exportação CSV, impressão/PDF, navegação e geolocalização com permissões; ponte restrita à origem local HTTPS.
- Corrigida a exceção `torchBtn` no site com guardas para quatro elementos opcionais, sem alterar cálculos, estilos ou páginas de preços.
- Adicionados testes de regressão web, testes unitários de validação de chaves e integração Android com modelos embarcados.
- Configurado GitHub Actions para testes, lint, build, verificação de assinatura, APK instalável com SHA-256, relatórios e testes em emulador. Instruções de instalação e limitações em `android/README.md`.

### Ajustes após validação

- Corrigido o provisionamento de CI para não solicitar o pacote SDK `tools`, removido do catálogo Android.
- Limitadas as resoluções de captura/análise e o tamanho de imagens da galeria para reduzir memória sem depender de desativação de recursos.
- Garantido que uma chave lida a partir da tela de preços volte à tela de notas, aguardando a ponte local ficar pronta. Adicionados testes desse retorno e das coordenadas/limites do editor de recorte.
- Declarados câmera/localização como recursos opcionais para preservar o uso de XMLs/galeria em aparelhos sem esses recursos; preservados os callbacks do WebView na rotação da tela.
- Registrada na auditoria uma falha pré-existente de apresentação de erros da Consulta 2, reproduzida com rede simulada nas três versões de preços.
