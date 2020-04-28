import cv2
import numpy as np
from scipy.ndimage import rotate
import argparse
import pytesseract
import pandas as pd
from intervaltree import IntervalTree, Interval


ap = argparse.ArgumentParser()
ap.add_argument("-i", "--image", help="Image to process", required=True)


def preprocess_image(img):
    h, w = img.shape[:2]
    img_greyscale = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    img_greyscale_filtered = cv2.GaussianBlur(img_greyscale, (5, 5), 0)
    clean = cv2.adaptiveThreshold(img_greyscale_filtered, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                  cv2.THRESH_BINARY_INV, 51, 5)

    kernel_1 = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    kernel_2 = cv2.getStructuringElement(cv2.MORPH_CROSS, (5, 5))
    # lighting can cause some black dots within letters
    clean = cv2.dilate(clean, kernel_1, iterations=1)
    clean = cv2.morphologyEx(clean, cv2.MORPH_CLOSE, kernel_2, iterations=1)

    contours, hierarchy = cv2.findContours(
        clean, cv2.RETR_LIST, cv2.CHAIN_APPROX_NONE)  # cv2.RETR_LIST ensures we get children in contour hierarchy

    letter_contours = []
    clean_to_draw = clean.copy()
    interval_tree = IntervalTree()

    for contour in contours:  # Draw on bounding boxes
        # get rectangle bounding contour
        [x, y, w_c, h_c] = cv2.boundingRect(contour)

        # Don't plot small false positives that aren't text
        # In a cropped Boggle board, letters occupy between 1/5 - 1/12 of a board dimension
        if w_c < (w/12) or h_c < (h/12) or w_c > (w/5) or h_c > (h/5):
            continue

        # draw rectangle around contour on original image for visualization
        cv2.rectangle(clean_to_draw, (x, y),
                      (x + w_c, y + h_c), (255, 0, 255), 2)

        letter_contour = clean[y:y+h_c, x:x+w_c]  # extract image region
        letter_contours.append(letter_contour)

        # we keep x in datum to sort with later on
        interval = Interval(y, y+h_c, [letter_contour, x])
        interval_tree.add(interval)

    # merges overlapping data points into lists

    # merge all overlapping intervals together. should squash to 4 Interval entries
    interval_tree.merge_overlaps(lambda a, b: list(a) + list(b))
    sorted_letter_contours = []
    # go in intervals from low to high y-coordinate (top to bottom of image)
    for interval in sorted(interval_tree, key=lambda x: x.begin):
        # sorted by x-coordinate from small to large
        contours = interval.data[::2]
        pos = interval.data[1::2]
        sorted_contours = [x for _, x in sorted(
            zip(pos, contours))]  # sorts by pos

        sorted_letter_contours += sorted_contours

    # write original image with added contours to disk
    # cv2.imshow('Segmented Image', clean_to_draw)
    # cv2.waitKey()

    return clean, sorted_letter_contours


def reduce_to_size(img, size_to_reach=(800, 800)):
    res = img.shape[:2]
    while np.any(np.greater(res, size_to_reach)):  # keep reducing size
        retain = 90  # 90 % retained at each step
        width = int(res[1] * retain / 100)
        height = int(res[0] * retain / 100)
        img = cv2.resize(
            img, (width, height), interpolation=cv2.INTER_AREA)
        res = img.shape[:2]

    return img


def contours_to_characters(contours):
    characters = []
    for contour in contours:
        h, w = contour.shape
        contour = cv2.copyMakeBorder(
            contour, 100, 100, 100, 100, cv2.BORDER_CONSTANT, value=[0, 0, 0])

        kernel_1 = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        # # kernel_2 = cv2.getStructuringElement(cv2.MORPH_CROSS, (2, 2))
        # # contour = cv2.dilate(contour, kernel_1, iterations=3)
        # contour = cv2.morphologyEx(
        #     contour, cv2.MORPH_CLOSE, kernel_1, iterations=2)

        contour = cv2.erode(contour, kernel_1, iterations=1)

        if w > h:
            contour_up = rotate(contour, 90)
        else:
            contour_up = contour

        contour_down = rotate(contour_up, 180)

        config = "--psm 10 -c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        text_up = pytesseract.image_to_data(
            contour_up, config=config, output_type='data.frame')
        text_down = pytesseract.image_to_data(
            contour_down, config=config, output_type='data.frame')

        up_pred = text_up.iloc[-1]['text']  # prediction
        up_conf = text_up.iloc[-1]['conf']  # confidence
        down_pred = text_down.iloc[-1]['text']
        down_conf = text_down.iloc[-1]['conf']

        # print(up_pred, down_pred)

        if up_pred == " " and down_pred == " ":
            pred = "S"  # default prediction
        elif up_pred == " ":
            pred = down_pred
        elif down_pred == " ":
            pred = up_pred
        else:
            pred = up_pred if up_conf >= down_conf else down_pred

        characters.append(pred.lower())

        # cv2.imshow('letter', contour_up)
        # cv2.waitKey()

    return(characters)


if __name__ == "__main__":
    args = vars(ap.parse_args())
    im_path = args['image']
    img = cv2.imread(im_path)
    img = reduce_to_size(img, (800, 800))

    img, contours = preprocess_image(img)

    print(contours_to_characters(contours))
