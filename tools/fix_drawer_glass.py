from pathlib import Path

path = Path('app/src/main/java/com/example/ui/AppNavGraph.kt')
text = path.read_text(encoding='utf-8')

anchor = '    val glassType by viewModel.userPreferences.glassType.collectAsState(initial = "soft")\n'
insert = anchor + '    val sharedGlassStyle = rememberGlassVisualStyle(viewModel)\n'
if 'val sharedGlassStyle = rememberGlassVisualStyle(viewModel)' not in text:
    if anchor not in text:
        raise SystemExit('Glass settings anchor not found')
    text = text.replace(anchor, insert, 1)

old_drawer = '''                if (isGlassTheme) Modifier
                    .background(drawerGlassBrush, RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.72f), RoundedCornerShape(28.dp))
                else Modifier
'''
new_drawer = '''                if (isGlassTheme) Modifier
                    .background(sharedGlassStyle.fill.copy(alpha = sharedGlassStyle.alpha), RoundedCornerShape(28.dp))
                    .border(1.dp, sharedGlassStyle.border, RoundedCornerShape(28.dp))
                else Modifier
'''
if old_drawer not in text:
    raise SystemExit('Drawer glass block not found')
text = text.replace(old_drawer, new_drawer, 1)

old_logout = '''                colors = if (isGlassTheme) {
                    ButtonDefaults.outlinedButtonColors(containerColor = Color.White.copy(alpha = glassSurfaceAlpha))
                } else ButtonDefaults.outlinedButtonColors()
'''
new_logout = '''                colors = if (isGlassTheme) {
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = sharedGlassStyle.fill.copy(alpha = sharedGlassStyle.alpha),
                        contentColor = sharedGlassStyle.highlight
                    )
                } else ButtonDefaults.outlinedButtonColors()
'''
if old_logout in text:
    text = text.replace(old_logout, new_logout, 1)

category_sig = '''fun CategoryItem(
    category: String,
    viewModel: MainViewModel,
    isExpanded: Boolean,
    accentBrush: Brush? = null,
    accentColor: Color? = null,
    onExpandToggle: () -> Unit
) {
    val count = viewModel.productsCountByCategory.collectAsState().value.firstOrNull { it.category == category }?.count ?: 0
    Column(modifier = Modifier.fillMaxWidth()) {
'''
category_new = '''fun CategoryItem(
    category: String,
    viewModel: MainViewModel,
    isExpanded: Boolean,
    accentBrush: Brush? = null,
    accentColor: Color? = null,
    onExpandToggle: () -> Unit
) {
    val count = viewModel.productsCountByCategory.collectAsState().value.firstOrNull { it.category == category }?.count ?: 0
    val glass = rememberGlassVisualStyle(viewModel)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (glass.enabled) Modifier
                    .background(glass.fill.copy(alpha = glass.alpha), shape)
                    .border(1.dp, glass.border, shape)
                else Modifier
            )
            .padding(vertical = if (glass.enabled) 2.dp else 0.dp)
    ) {
'''
if category_sig not in text:
    raise SystemExit('CategoryItem signature block not found')
text = text.replace(category_sig, category_new, 1)

old_text_color = '                color = accentColor ?: MaterialTheme.colorScheme.primary,\n'
new_text_color = '                color = if (glass.enabled) glass.highlight else (accentColor ?: MaterialTheme.colorScheme.primary),\n'
# only within CategoryItem: use rsplit from its start
idx = text.index('fun CategoryItem(')
pre, post = text[:idx], text[idx:]
post = post.replace(old_text_color, new_text_color, 1)
old_icon_tint = '                tint = accentColor ?: MaterialTheme.colorScheme.primary\n'
new_icon_tint = '                tint = if (glass.enabled) glass.highlight else (accentColor ?: MaterialTheme.colorScheme.primary)\n'
post = post.replace(old_icon_tint, new_icon_tint, 1)
text = pre + post

path.write_text(text, encoding='utf-8')
print('Drawer/category Glass unified with shared style')
