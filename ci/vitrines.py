"""Visuels du README (doc/images) : écrans posés sur un fond gris dégradé, dans
des cadres de téléphone ou de fenêtre, à la manière des présentations produit.

Entrées :
- build/captures : rendus hors écran de l'appli (PC, déverrouillage, ouverture)
  KEEPLUS_CAPTURES=1 ./gradlew :desktop:test --tests "*ReadmeScreenshotsTest*"
- build/captures/telephone : captures de l'émulateur Android (barre d'état en
  mode démo, 9:41), protection contre les captures levée le temps de la prise :
  liste, fiche, generateur, reglages (coffre de démonstration), pastille,
  carte-connexion, carte-inscription (appli Démo Connexion).
Lancer depuis la racine du dépôt : python ci/vitrines.py"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont

kRaw   = 'build/captures/'
kPhone = 'build/captures/telephone/'
kOut   = 'doc/images/'
kFont  = 'C:/Windows/Fonts/seguisb.ttf'


def background(inW, inH):
	"""Fond gris dégradé, plus clair en haut à gauche, avec un léger vignettage."""
	vImg = Image.new('RGB', (inW, inH))
	vPx  = vImg.load()
	for vY in range(inH):
		for vX in range(0, inW, 1):
			vT = (vX / inW) * 0.35 + (vY / inH) * 0.65
			vC = int(158 - 46 * vT)
			vPx[vX, vY] = (vC, vC, vC + 2)
	return vImg


def rounded_mask(inSize, inRadius):
	"""Masque d'un rectangle arrondi (anti-crénelé par sur-échantillonnage)."""
	vScale = 4
	vBig   = Image.new('L', (inSize[0] * vScale, inSize[1] * vScale), 0)
	ImageDraw.Draw(vBig).rounded_rectangle((0, 0, vBig.width - 1, vBig.height - 1), inRadius * vScale, fill=255)
	return vBig.resize(inSize, Image.LANCZOS)


def shadow(inCanvas, inBox, inRadius, inBlur=60, inOpacity=110, inOffset=(0, 40)):
	"""Ombre douce sous un élément."""
	vLayer = Image.new('L', inCanvas.size, 0)
	vBox   = (inBox[0] + inOffset[0], inBox[1] + inOffset[1], inBox[2] + inOffset[0], inBox[3] + inOffset[1])
	ImageDraw.Draw(vLayer).rounded_rectangle(vBox, inRadius, fill=inOpacity)
	vLayer = vLayer.filter(ImageFilter.GaussianBlur(inBlur))
	inCanvas.paste(Image.new('RGB', inCanvas.size, (20, 20, 22)), (0, 0), vLayer)


def clean_status(inScreen):
	"""Efface l'icône parasite de la barre d'état de l'émulateur (bouclier à gauche)."""
	vImg  = inScreen.copy()
	for vX in range(130, 190):
		for vY in range(40, 110):
			vImg.putpixel((vX, vY), vImg.getpixel((205, vY)))
	return vImg


def add_status(inScreen, inDark=True):
	"""Barre d'état dessinée (9:41, Wi-Fi, batterie) au-dessus d'un écran rendu sans
	barre : une bande prolonge le haut de l'écran, le contenu descend d'autant."""
	vW    = inScreen.width
	vBand = int(vW * 0.115)
	vImg  = Image.new('RGB', (vW, inScreen.height + vBand))
	vImg.paste(inScreen.crop((0, 0, vW, 1)).resize((vW, vBand)), (0, 0))
	vImg.paste(inScreen, (0, vBand))
	vDraw = ImageDraw.Draw(vImg)
	vInk  = (245, 245, 245) if inDark else (20, 20, 20)
	vFont = ImageFont.truetype(kFont, int(vW * 0.034))
	vDraw.text((int(vW * 0.075), int(vW * 0.042)), '9:41', font=vFont, fill=vInk)
	vBx = int(vW * 0.855); vBy = int(vW * 0.048); vBw = int(vW * 0.062); vBh = int(vW * 0.029)
	vDraw.rounded_rectangle((vBx, vBy, vBx + vBw, vBy + vBh), vBh // 3, fill=vInk)
	vDraw.rectangle((vBx + vBw + 3, vBy + vBh // 3, vBx + vBw + 7, vBy + vBh - vBh // 3), fill=vInk)
	vCx = int(vW * 0.80); vCy = vBy + vBh
	for vR, vWd in ((int(vW * 0.030), 4), (int(vW * 0.020), 4)):
		vDraw.arc((vCx - vR, vCy - vR, vCx + vR, vCy + vR), 225, 315, fill=vInk, width=vWd)
	vDraw.ellipse((vCx - 5, vCy - 9, vCx + 5, vCy + 1), fill=vInk)
	return vImg


def phone(inScreen, inWidth):
	"""Téléphone : écran arrondi dans un cadre sombre à liseré métallique, îlot noir en haut."""
	vScreen = inScreen.convert('RGB')
	vSw     = inWidth
	vSh     = int(vScreen.height * inWidth / vScreen.width)
	vScreen = vScreen.resize((vSw, vSh), Image.LANCZOS)
	vBezel  = int(inWidth * 0.032)
	vEdge   = max(3, int(inWidth * 0.008))
	vW      = vSw + 2 * (vBezel + vEdge)
	vH      = vSh + 2 * (vBezel + vEdge)
	vR      = int(inWidth * 0.135)
	vBody   = Image.new('RGBA', (vW, vH), (0, 0, 0, 0))
	vMetal  = Image.new('RGB', (vW, vH))
	vMd     = ImageDraw.Draw(vMetal)
	for vY in range(vH):
		vC = int(118 - 70 * vY / vH)
		vMd.line((0, vY, vW, vY), fill=(vC + 14, vC + 14, vC + 18))
	vBody.paste(vMetal, (0, 0), rounded_mask((vW, vH), vR))
	vInner = rounded_mask((vW - 2 * vEdge, vH - 2 * vEdge), vR - vEdge)
	vBody.paste(Image.new('RGB', (vW - 2 * vEdge, vH - 2 * vEdge), (12, 12, 13)), (vEdge, vEdge), vInner)
	vBody.paste(vScreen, (vEdge + vBezel, vEdge + vBezel), rounded_mask((vSw, vSh), vR - vEdge - vBezel))
	vD      = ImageDraw.Draw(vBody)
	vIw     = int(vSw * 0.29); vIh = int(vSw * 0.083)
	vIx     = (vW - vIw) // 2; vIy = vEdge + vBezel + int(vSw * 0.026)
	vD.rounded_rectangle((vIx, vIy, vIx + vIw, vIy + vIh), vIh // 2, fill=(0, 0, 0))
	vD.ellipse((vIx + vIw - vIh * 0.85, vIy + vIh * 0.25, vIx + vIw - vIh * 0.35, vIy + vIh * 0.75), fill=(28, 30, 40))
	return vBody


def flat(inScreen, inWidth):
	"""Écran seul, sans cadre, coins arrondis (comme l'écran de gauche de l'inspiration)."""
	vScreen = inScreen.convert('RGB')
	vH      = int(vScreen.height * inWidth / vScreen.width)
	vScreen = vScreen.resize((inWidth, vH), Image.LANCZOS)
	vOut    = Image.new('RGBA', (inWidth, vH), (0, 0, 0, 0))
	vOut.paste(vScreen, (0, 0), rounded_mask((inWidth, vH), int(inWidth * 0.11)))
	return vOut


def window(inShot, inWidth, inRadius=18):
	"""Fenêtre de PC : capture aux coins arrondis, liseré fin."""
	vShot = inShot.convert('RGB')
	vH    = int(vShot.height * inWidth / vShot.width)
	vShot = vShot.resize((inWidth, vH), Image.LANCZOS)
	vOut  = Image.new('RGBA', (inWidth + 2, vH + 2), (0, 0, 0, 0))
	vOut.paste(Image.new('RGB', vOut.size, (70, 70, 74)), (0, 0), rounded_mask(vOut.size, inRadius + 1))
	vOut.paste(vShot, (1, 1), rounded_mask((inWidth, vH), inRadius))
	return vOut


def place(inCanvas, inItem, inX, inY, inRadius, inBlur=55):
	"""Pose un élément avec son ombre."""
	shadow(inCanvas, (inX, inY, inX + inItem.width, inY + inItem.height), inRadius, inBlur)
	inCanvas.paste(inItem, (inX, inY), inItem)


def save(inCanvas, inName, inWidth=1600):
	"""Enregistre un visuel à la largeur du README."""
	vH = int(inCanvas.height * inWidth / inCanvas.width)
	inCanvas.resize((inWidth, vH), Image.LANCZOS).save(kOut + inName, optimize=True)
	print(inName, (inWidth, vH))


def emu(inName):
	"""Capture de l'émulateur, barre d'état nettoyée."""
	return clean_status(Image.open(kPhone + inName).convert('RGB'))


# 1. Couverture : liste à plat à gauche, déverrouillage dans un téléphone coupé à droite.
vCanvas = background(3200, 2000)
vLeft   = flat(emu('liste.png'), 760)
place(vCanvas, vLeft, 330, 300, 80)
vUnlock = add_status(Image.open(kRaw + 'telephone-deverrouillage.png').convert('RGB'))
vRight  = phone(vUnlock, 1180)
place(vCanvas, vRight, 1520, 40, 160, 70)
save(vCanvas, 'vitrine-telephone.png')

# 2. Auto-remplissage : pastille du clavier, carte de connexion, carte d'inscription.
vCanvas = background(3200, 2050)
for vIndex, vName in enumerate(['pastille.png', 'carte-connexion.png', 'carte-inscription.png']):
	vItem = phone(emu(vName), 820)
	place(vCanvas, vItem, 190 + vIndex * 1000, 140 + (0 if vIndex == 1 else 60), 110)
save(vCanvas, 'telephone-auto-remplissage.png')

# 3. Écrans du téléphone : fiche, générateur, réglages.
vCanvas = background(3200, 2050)
for vIndex, vName in enumerate(['fiche.png', 'generateur.png', 'reglages.png']):
	vItem = phone(emu(vName), 820)
	place(vCanvas, vItem, 190 + vIndex * 1000, 140 + (0 if vIndex == 1 else 60), 110)
save(vCanvas, 'telephone-ecrans.png')

# 4. PC : la fenêtre du coffre.
vCanvas = background(3200, 2100)
vWin    = window(Image.open(kRaw + 'vitrine-pc-coffre.png'), 2700)
place(vCanvas, vWin, 250, 160, 30, 70)
save(vCanvas, 'vitrine-pc.png')

# 5. PC : la carte de saisie en haut d'un navigateur.
vCanvas = background(3200, 1900)
vWin    = window(Image.open(kRaw + 'pc-saisie-rapide.png'), 2700)
place(vCanvas, vWin, 250, 160, 30, 70)
save(vCanvas, 'pc-saisie-rapide.png')

# 6. PC : appairage d'un téléphone.
vCanvas = background(3200, 2100)
vWin    = window(Image.open(kRaw + 'pc-appairage.png'), 2700)
place(vCanvas, vWin, 250, 160, 30, 70)
save(vCanvas, 'pc-appairage.png')

# 7. Ouverture : les trois temps de l'animation dans des téléphones.
vCanvas = background(3200, 2050)
for vIndex in range(3):
	vFrame = add_status(Image.open(kRaw + 'ouverture-%d.png' % (vIndex + 1)).convert('RGB'), False)
	vItem  = phone(vFrame, 820)
	place(vCanvas, vItem, 190 + vIndex * 1000, 140 + (0 if vIndex == 1 else 60), 110)
save(vCanvas, 'ouverture.png')
