# Дизайн и исследование Android документации — OALab

В рамках проектирования были просмотрены более ста страниц официальной документации Android/Google: Material 3, Compose-компоненты, состояние и макеты, изображения, анимации, доступность, отклик интерфейса, производительность и Media3. Ниже — **выборка ключевых ссылок**, применимых к архитектуре OALab, а не выдуманный список 100 независимых фич.

## Направление дизайна
- Чёрный фон #09090B, поверхности #17171A и #222226, светлый текст #F5F5F5.
- Без дополнительных цветов, динамического Material You и декоративных градиентов палитры.
- У карточек постеров радиус 18dp; у герой-карточек 28dp, у нижней навигации 40dp.
- Плавающий pill-bar с пятью направлениями. Интерактивные области крупных кнопок ориентированы на 44–48dp.
- Приоритет обложек, надписей на тёмном градиенте, понятного состояния выделения, прогресса и пустых экранов.
- Озвучки, языки, оценки, рейтинги и тайминги в одном bottom sheet; на экране тайтла доступен тот же выбор до начала просмотра.
- Предусмотрены системные кнопки возврата, системные отступы, заглушки и сообщения об ошибках.

## Гайдлайны и инженерные статьи

### Material 3 и компоненты
1. [Material 3](https://developer.android.com/develop/ui/compose/designsystems/material3)
2. [Обзор компонентов](https://developer.android.com/develop/ui/compose/components)
3. [Кнопки](https://developer.android.com/develop/ui/compose/components/button)
4. [Иконки-кнопки](https://developer.android.com/develop/ui/compose/components/icon-button)
5. [Сегментированные кнопки](https://developer.android.com/develop/ui/compose/components/segmented-button)
6. [Бейджи](https://developer.android.com/develop/ui/compose/components/badges)
7. [Индикаторы загрузки](https://developer.android.com/develop/ui/compose/components/progress)
8. [Snackbar](https://developer.android.com/develop/ui/compose/components/snackbar)
9. [Tooltip](https://developer.android.com/develop/ui/compose/components/tooltip)
10. [Нижние панели](https://developer.android.com/develop/ui/compose/components/bottom-sheets)
11. [Карточки](https://developer.android.com/develop/ui/compose/components/card)
12. [Карусели](https://developer.android.com/develop/ui/compose/components/carousel)
13. [Диалоги](https://developer.android.com/develop/ui/compose/components/dialog)
14. [Разделители](https://developer.android.com/develop/ui/compose/components/divider)
15. [Списки](https://developer.android.com/develop/ui/compose/lists)
16. [Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold)
17. [Верхние панели](https://developer.android.com/develop/ui/compose/components/app-bars)
18. [Нижняя навигация](https://developer.android.com/develop/ui/compose/components/navigation-bar)
19. [Navigation drawer](https://developer.android.com/develop/ui/compose/components/drawer)
20. [Navigation rail](https://developer.android.com/develop/ui/compose/components/navigation-rail)
21. [Вкладки](https://developer.android.com/develop/ui/compose/components/tabs)
22. [Checkbox](https://developer.android.com/develop/ui/compose/components/checkbox)
23. [Chips](https://developer.android.com/develop/ui/compose/components/chip)
24. [Выбор даты](https://developer.android.com/develop/ui/compose/components/datepickers)
25. [Меню](https://developer.android.com/develop/ui/compose/components/menu)
26. [Radio button](https://developer.android.com/develop/ui/compose/components/radio-button)
27. [Slider](https://developer.android.com/develop/ui/compose/components/slider)
28. [Switch](https://developer.android.com/develop/ui/compose/components/switch)
29. [Time pickers](https://developer.android.com/develop/ui/compose/components/time-pickers)
30. [Search](https://developer.android.com/develop/ui/compose/components/search-bar)

### Макеты, состояние, текст
31. [Layouts](https://developer.android.com/develop/ui/compose/layouts)
32. [Adaptive layouts](https://developer.android.com/develop/ui/compose/layouts/adaptive/get-started-with-adaptive-apps)
33. [Pager](https://developer.android.com/develop/ui/compose/layouts/pager)
34. [System insets](https://developer.android.com/develop/ui/compose/system/insets)
35. [State in Compose](https://developer.android.com/develop/ui/compose/state)
36. [Text in Compose](https://developer.android.com/develop/ui/compose/text)
37. [Text field configuration](https://developer.android.com/develop/ui/compose/text/user-input)
38. [Text styles](https://developer.android.com/develop/ui/compose/text/style-text)
39. [Input](https://developer.android.com/develop/ui/compose/touch-input/pointer-input)
40. [Scrolling](https://developer.android.com/develop/ui/compose/touch-input/scroll/scroll-modifiers)
41. [Focus](https://developer.android.com/develop/ui/compose/touch-input/focus)
42. [Interactions](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions)

### Изображения, движения и производительность
43. [Graphics](https://developer.android.com/develop/ui/compose/graphics)
44. [Loading images](https://developer.android.com/develop/ui/compose/graphics/images/loading)
45. [Raster and vector](https://developer.android.com/develop/ui/compose/graphics/images/compare)
46. [Icons](https://developer.android.com/develop/ui/compose/graphics/images/material)
47. [Customizing image](https://developer.android.com/develop/ui/compose/graphics/images/customize)
48. [Custom painter](https://developer.android.com/develop/ui/compose/graphics/images/custompainter)
49. [Image optimization](https://developer.android.com/develop/ui/compose/graphics/images/optimization)
50. [Shapes](https://developer.android.com/develop/ui/compose/graphics/draw/shapes)
51. [Graphics modifiers](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers)
52. [Animation introduction](https://developer.android.com/develop/ui/compose/animation/introduction)
53. [Animation quick guide](https://developer.android.com/develop/ui/compose/animation/quick-guide)
54. [Animate content](https://developer.android.com/develop/ui/compose/animation/composables-modifiers)
55. [Value animations](https://developer.android.com/develop/ui/compose/animation/value-based)
56. [Customize animations](https://developer.android.com/develop/ui/compose/animation/customize)
57. [Choose animation API](https://developer.android.com/develop/ui/compose/animation/choose-api)
58. [Shared elements](https://developer.android.com/develop/ui/compose/animation/shared-elements)
59. [Animation testing](https://developer.android.com/develop/ui/compose/animation/testing)
60. [Animation tooling](https://developer.android.com/develop/ui/compose/animation/tooling)
61. [Performance](https://developer.android.com/develop/ui/compose/performance)
62. [Compose phases](https://developer.android.com/develop/ui/compose/performance/phases)
63. [Performance practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
64. [Stability](https://developer.android.com/develop/ui/compose/performance/stability)
65. [Diagnosing stability](https://developer.android.com/develop/ui/compose/performance/stability/diagnose)
66. [Fixing stability](https://developer.android.com/develop/ui/compose/performance/stability/fix)
67. [Baseline profiles](https://developer.android.com/develop/ui/compose/performance/baseline-profiles)
68. [Performance tooling](https://developer.android.com/develop/ui/compose/performance/tooling)
69. [Backwards writes](https://developer.android.com/develop/ui/compose/performance/backwards-write)

### Media3 и поддержка экранного воспроизведения
70. [Media3 overview](https://developer.android.com/media/media3)
71. [ExoPlayer overview](https://developer.android.com/media/media3/exoplayer)
72. [ExoPlayer setup](https://developer.android.com/media/media3/exoplayer/hello-world)
73. [Track selection](https://developer.android.com/media/media3/exoplayer/track-selection)
74. [HLS](https://developer.android.com/media/media3/exoplayer/hls)
75. [Media3 Compose UI](https://developer.android.com/media/media3/ui/compose)
76. [Media3 Compose customization](https://developer.android.com/media/media3/ui/compose-customization)
77. [PlayerView](https://developer.android.com/reference/androidx/media3/ui/PlayerView)
78. [ExoPlayer API](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer)
79. [Player API](https://developer.android.com/reference/androidx/media3/common/Player)
80. [MediaSession](https://developer.android.com/reference/androidx/media3/session/MediaSession)
81. [MediaController](https://developer.android.com/reference/androidx/media3/session/MediaController)
82. [MediaBrowser](https://developer.android.com/reference/androidx/media3/session/MediaBrowser)
83. [Media3 UI overview](https://developer.android.com/media/media3/ui/overview)

### Сборка и интеграция
84. [Compose BOM](https://developer.android.com/develop/ui/compose/bom)
85. [BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping)
86. [Compose Compiler](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
87. [AGP 8.13 release](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
88. [Android Media3 release](https://developer.android.com/jetpack/androidx/releases/media3)
89. [Compose release](https://developer.android.com/jetpack/androidx/releases/compose)
90. [AniList GraphQL](https://docs.anilist.co/guide/graphql/queries/media)
91. [AniList rate limit](https://docs.anilist.co/guide/rate-limiting)
92. [Jikan API](https://docs.api.jikan.moe/)

Примечание: часть ссылок ведёт на обзорные разделы, часть — на узкие компоненты. Библиотека версий зафиксирована не по принципу «последняя любой ценой», а по фактически проверенной совместимости с Android Gradle Plugin. UI-проверка на реальном устройстве не заменяется чтением документации.
