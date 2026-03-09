# HMDM Server — Design System

Sistema moderno, minimalista, foco em performance e UX para painel MDM.

## Padrão
- **Nome:** Minimal Single Column / Data-Dense Dashboard
- **Foco:** Dados em evidência, tipografia clara, espaço em branco, sem ruído visual
- **CTA:** Contraste alto (7:1+), botões com cor de destaque

## Cores
| Papel        | Hex       | Uso                    |
|-------------|-----------|------------------------|
| Primary     | `#171717` | Texto, header, bordas  |
| Secondary   | `#404040` | Texto secundário       |
| Accent/CTA  | `#D4AF37` | Botões principais, links |
| Background  | `#FFFFFF` | Fundo                   |
| Surface     | `#F5F5F5` | Cards, listas          |
| Border      | `#E5E5E5` | Bordas                 |

## Tipografia
- **Fonte:** Inter (300, 400, 500, 600, 700)
- **Uso:** Dashboards, admin, documentação, enterprise

## Efeitos
- Hover: transição 150–300ms
- cursor-pointer em todos os clicáveis
- Focus visível para acessibilidade
- prefers-reduced-motion respeitado

## Anti-padrões a evitar
- Emojis como ícones (usar SVG: Heroicons/Lucide)
- Bordas/glass invisíveis em light mode
- Hover com scale que desloca layout
